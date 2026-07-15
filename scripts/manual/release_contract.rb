require "open3"
require "pathname"
require "set"
require "yaml"

module ManualDocs
  class ReleaseContract
    ValidationResult = Struct.new(:errors, :checked_count, :source_path_count, keyword_init: true)
    LINK_PATTERN = /!?\[[^\]]*\]\(\s*<?([^)>\s]+)>?(?:\s+["'][^)]*["'])?\s*\)/

    def initialize(repository_root:, tag:, expected_sha:, manifest_path: nil, git_runner: nil)
      @repository_root = File.expand_path(repository_root)
      @tag = tag
      @expected_sha = expected_sha
      @manifest_path = manifest_path && File.expand_path(manifest_path, @repository_root)
      @git_runner = git_runner || method(:run_git)
    end

    def errors
      validate.errors
    end

    def validate
      type, success = @git_runner.call(["cat-file", "-t", "refs/tags/#{@tag}"])
      return result(["release tag not found: #{@tag}"]) unless success
      return result(["release tag must be annotated: #{@tag}"]) unless type.strip == "tag"
      sha, success = @git_runner.call(["rev-parse", "--verify", "refs/tags/#{@tag}^{commit}"])
      return result(["release tag not found: #{@tag}"]) unless success
      sha = sha.strip
      return result(["release tag #{@tag} resolves to #{sha}, expected #{@expected_sha}"]) unless sha.casecmp?(@expected_sha)
      output, success = @git_runner.call(["ls-tree", "-r", "--name-only", sha])
      return result(["release tree could not be read: #{sha}"]) unless success
      tree = output.lines(chomp: true).to_set
      links = repository_links
      errors = links.each_with_object([]) do |(file, line, target), result|
        path = Pathname.new(File.dirname(file)).join(target.split(/[?#]/, 2).first).cleanpath.to_s
        if path == ".." || path.start_with?("../") || Pathname.new(path).absolute?
          result << "#{file}:#{line}: unsafe release path: #{target}"
        elsif !tree.include?(path)
          result << "#{file}:#{line}: release path not found: #{path}"
        end
      end
      github_errors, github_count = validate_github_release_links(tree)
      errors.concat(github_errors)
      source_path_errors, source_path_count = validate_manifest_source_paths(tree)
      errors.concat(source_path_errors)
      result(errors.sort, links.length + github_count, source_path_count)
    end

    private

    def repository_links
      Dir.glob(File.join(@repository_root, "docs/manual/**/*.md")).sort.flat_map do |path|
        file = Pathname.new(path).relative_path_from(Pathname.new(@repository_root)).to_s
        content = File.read(path)
        content.to_enum(:scan, LINK_PATTERN).each_with_object([]) do |_captures, result|
          match = Regexp.last_match
          target = match[1]
          next unless repository_target?(file, target)
          result << [file, content[0...match.begin(0)].count("\n") + 1, target]
        end
      end
    end

    def validate_github_release_links(tree)
      links = Dir.glob(File.join(@repository_root, "docs/manual/**/*.md")).sort.flat_map do |path|
        file = Pathname.new(path).relative_path_from(Pathname.new(@repository_root)).to_s
        content = File.read(path)
        content.to_enum(:scan, LINK_PATTERN).map do
          match = Regexp.last_match
          target = match[1]
          source = target.match(%r{\Ahttps://github\.com/bluetape4k/bluetape4k-graph/blob/([^/]+)/(.+)\z})
          next unless source
          [file, content[0...match.begin(0)].count("\n") + 1, source[1], source[2].split(/[?#]/, 2).first]
        end.compact
      end
      errors = links.each_with_object([]) do |(file, line, ref, path), result|
        result << "#{file}:#{line}: source link commit #{ref}, expected #{@expected_sha}" unless ref.casecmp?(@expected_sha)
        result << "#{file}:#{line}: release path not found: #{path}" unless tree.include?(path)
      end
      [errors, links.length]
    end

    def validate_manifest_source_paths(tree)
      return [[], 0] unless @manifest_path && File.file?(@manifest_path)
      manifest = YAML.safe_load(File.read(@manifest_path))
      paths = Array(manifest["modules"]).flat_map do |entry|
        Array(entry["sourcePaths"]).map do |path|
          unsafe = path.to_s.empty? || Pathname.new(path.to_s).absolute? || Pathname.new(path.to_s).each_filename.any? { |part| part == ".." }
          present = !unsafe && tree.any? { |release_path| release_path == path || release_path.start_with?("#{path}/") }
          "#{entry['id']}: sourcePath not found in release tree: #{path}" unless present
        end.compact
      end
      [paths, Array(manifest["modules"]).sum { |entry| Array(entry["sourcePaths"]).length }]
    rescue Psych::SyntaxError => error
      [["manual manifest YAML is invalid: #{error.problem}"], 0]
    end

    def repository_target?(file, target)
      return false if target.empty? || target.start_with?("#", "/") || target.match?(/\A[a-z][a-z0-9+.-]*:/i)
      candidate = Pathname.new(File.dirname(file)).join(target.split(/[?#]/, 2).first).cleanpath.to_s
      candidate != "docs/manual" && !candidate.start_with?("docs/manual/")
    end

    def result(errors, count = 0, source_path_count = 0)
      ValidationResult.new(errors: errors, checked_count: count, source_path_count: source_path_count)
    end

    def run_git(arguments)
      stdout, _stderr, status = Open3.capture3("git", "-C", @repository_root, *arguments)
      [stdout, status.success?]
    end
  end
end

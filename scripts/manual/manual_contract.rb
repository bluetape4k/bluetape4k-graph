require "pathname"
require "yaml"

module ManualDocs
  SUPPORTED_SCHEMA_VERSION = 2
  VALID_KINDS = %w[library benchmark example].freeze

  class Validator
    REQUIRED_FIELDS = %w[id gradlePath projectName sourceDir kind artifact status].freeze
    LOCALES = { "en" => "English", "ko" => "Korean" }.freeze
    attr_reader :errors

    def initialize(inventory:, manifest_path:, repository_root:, expected_release:, strict: false)
      @inventory = inventory
      @manifest_path = File.expand_path(manifest_path)
      @repository_root = File.expand_path(repository_root)
      @expected_release = expected_release
      @strict = strict
      @errors = validate.sort
    end

    private

    def validate
      return ["manual manifest not found"] unless File.file?(@manifest_path)
      manifest = YAML.safe_load(File.read(@manifest_path))
      return ["manual manifest must be a mapping"] unless manifest.is_a?(Hash)
      errors = validate_header(manifest)
      modules = manifest["modules"]
      return errors << "manual manifest modules must be an array" unless modules.is_a?(Array)
      entries = modules.select { |entry| entry.is_a?(Hash) }
      errors.concat(duplicates(entries, "id").map { |id| "duplicate id #{id}" })
      errors.concat(duplicates(entries, "gradlePath").map { |path| "duplicate gradlePath #{path}" })
      errors.concat(validate_inventory(entries))
      entries.each { |entry| errors.concat(validate_entry(entry)) }
      errors.concat(validate_assets(manifest.fetch("assets", []), "manifest"))
      errors.concat(validate_overview(manifest, entries))
      errors
    rescue Psych::SyntaxError => error
      ["manual manifest YAML is invalid: #{error.problem}"]
    end

    def validate_header(manifest)
      errors = []
      errors << "manual manifest schemaVersion must be #{SUPPORTED_SCHEMA_VERSION}" unless manifest["schemaVersion"] == SUPPORTED_SCHEMA_VERSION
      errors << "manual manifest repository must be bluetape4k-graph" unless manifest["repository"] == "bluetape4k-graph"
      errors << "manual manifest releaseRef must be #{@expected_release.fetch('ref')}" unless manifest["releaseRef"] == @expected_release.fetch("ref")
      errors << "manual manifest stableVersion must be #{@expected_release.fetch('ref')}" unless manifest["stableVersion"] == @expected_release.fetch("ref")
      errors << "manual manifest stableMinor must be #{@expected_release.fetch('ref').split('.')[0, 2].join('.')}" unless manifest["stableMinor"] == @expected_release.fetch("ref").split(".")[0, 2].join(".")
      errors << "manual manifest releaseTag must be #{@expected_release.fetch('ref')}" unless manifest["releaseTag"] == @expected_release.fetch("ref")
      errors << "manual manifest releaseCommit must be #{@expected_release.fetch('commit')}" unless manifest["releaseCommit"] == @expected_release.fetch("commit")
      publication = manifest["publication"]
      unless publication.is_a?(Hash)
        errors << "manual publication must be a mapping"
        return errors
      end
      locales = publication["locales"]
      errors << "manual publication locales must be en and ko" unless locales == %w[en ko]
      if @strict
        errors << "manual publication manualVersion must be 0.5" unless publication["manualVersion"] == "0.5"
        errors << "manual publication sourceRoot must be docs/manual" unless publication["sourceRoot"] == "docs/manual"
        errors << "manual publication contentStatus must be complete" unless publication["contentStatus"] == "complete"
      end
      errors
    end

    def validate_inventory(entries)
      return ["module inventory must be an array"] unless @inventory.is_a?(Array)
      by_inventory = @inventory.to_h { |row| [row["gradlePath"], row] }
      by_manifest = entries.to_h { |row| [row["gradlePath"], row] }
      errors = duplicates(@inventory, "gradlePath").map { |path| "inventory: duplicate gradlePath #{path}" }
      (by_inventory.keys - by_manifest.keys).sort.each { |path| errors << "#{path}: missing from manifest" }
      (by_manifest.keys - by_inventory.keys).sort.each { |path| errors << "#{path}: not present in inventory" }
      entries.each do |entry|
        row = by_inventory[entry["gradlePath"]]
        next unless row
        %w[projectName sourceDir kind].each { |field| errors << "#{entry['id']}: #{field} does not match inventory" unless entry[field] == row[field] }
      end
      errors
    end

    def validate_entry(entry)
      errors = []
      REQUIRED_FIELDS.each { |field| errors << "#{entry['id'] || 'module'}: missing manifest field #{field}" unless entry.key?(field) }
      errors << "#{entry['id']}: invalid kind #{entry['kind'].inspect}" unless VALID_KINDS.include?(entry["kind"])
      errors << "#{entry['id']}: library artifact must be present" if entry["kind"] == "library" && blank?(entry["artifact"])
      errors << "#{entry['id']}: #{entry['kind']} artifact must be null" if %w[benchmark example].include?(entry["kind"]) && !entry["artifact"].nil?
      errors << "#{entry['id']}: missing manifest field sourcePaths" if @strict && !entry.key?("sourcePaths")
      if @strict && entry.key?("sourcePaths") && entry["sourcePaths"] != [entry["sourceDir"]]
        errors << "#{entry['id']}: sourcePaths must equal [sourceDir]"
      end
      errors.concat(validate_paths(entry, "sourcePaths")) if entry.key?("sourcePaths")
      errors.concat(validate_routes(entry)) if entry.key?("routes") || @strict
      errors.concat(validate_assets(entry.fetch("assets", []), entry["id"]))
      errors
    end

    def validate_overview(manifest, entries)
      overview = manifest["overview"]
      return @strict ? ["manual manifest overview must be a mapping"] : [] unless overview.is_a?(Hash)
      documents = overview["documents"]
      return ["manual overview documents must be a mapping"] unless documents.is_a?(Hash)

      errors = []
      locale_paths = {}
      LOCALES.each do |locale, language|
        paths = documents[locale]
        unless paths.is_a?(Array)
          errors << "manual overview #{language} documents must be an array"
          next
        end
        locale_paths[locale] = paths
        duplicates(paths.map { |path| { "path" => path } }, "path").each do |path|
          errors << "manual overview duplicate #{language} document #{path}"
        end
        paths.each do |path|
          unless safe_relative?(path) && path.start_with?("#{locale}/")
            errors << "manual overview unsafe #{language} document #{path}"
            next
          end
          absolute = File.expand_path(path, File.dirname(@manifest_path))
          if !within?(absolute, File.dirname(@manifest_path)) || !File.file?(absolute)
            errors << "manual overview missing #{language} document #{path}"
          else
            errors.concat(validate_document_references(absolute, "overview"))
          end
        end
      end
      if locale_paths.keys.sort == LOCALES.keys.sort
        en_slugs = locale_paths.fetch("en").map { |path| path.delete_prefix("en/") }
        ko_slugs = locale_paths.fetch("ko").map { |path| path.delete_prefix("ko/") }
        errors << "manual overview English/Korean routes differ" unless en_slugs == ko_slugs
      end
      errors.concat(validate_assets(overview.fetch("assets", []), "overview"))
      errors.concat(validate_registered_documents(entries, locale_paths)) if @strict
      errors
    end

    def validate_registered_documents(entries, locale_paths)
      manual_root = File.dirname(@manifest_path)
      LOCALES.keys.flat_map do |locale|
        registered = Array(locale_paths[locale]) + entries.map { |entry| entry.dig("routes", locale) }.compact
        actual = Dir.glob(File.join(manual_root, locale, "**/*.md")).map do |path|
          Pathname.new(path).relative_path_from(Pathname.new(manual_root)).to_s
        end
        (actual - registered).sort.map { |path| "unregistered manual document #{path}" } +
          (registered - actual).sort.map { |path| "registered manual document not found #{path}" }
      end
    end

    def validate_routes(entry)
      routes = entry["routes"]
      return ["#{entry['id']}: routes must be a mapping"] unless routes.is_a?(Hash)
      errors = []
      LOCALES.each do |locale, language|
        path = routes[locale]
        if blank?(path)
          errors << "#{entry['id']}: missing #{language} route"
          next
        end
        unless safe_relative?(path) && path.start_with?("#{locale}/")
          errors << "#{entry['id']}: unsafe #{language} route"
          next
        end
        absolute = File.expand_path(path, File.dirname(@manifest_path))
        if !within?(absolute, File.dirname(@manifest_path)) || !File.file?(absolute)
          errors << "#{entry['id']}: missing #{language} document"
        else
          errors.concat(validate_document_references(absolute, entry["id"]))
        end
      end
      if routes["en"].is_a?(String) && routes["ko"].is_a?(String)
        errors << "#{entry['id']}: English/Korean route differs" unless routes["en"].delete_prefix("en/") == routes["ko"].delete_prefix("ko/")
      end
      errors
    end

    def validate_paths(entry, field)
      paths = entry[field]
      return ["#{entry['id']}: #{field} must be an array"] unless paths.is_a?(Array)
      return ["#{entry['id']}: #{field} must contain non-empty strings"] unless paths.all? { |path| path.is_a?(String) && !path.empty? }
      paths.each_with_object([]) do |path, errors|
        absolute = File.expand_path(path.to_s, @repository_root)
        unless safe_relative?(path) && within?(absolute, @repository_root) && File.exist?(absolute) && within?(File.realpath(absolute), File.realpath(@repository_root))
          errors << "#{entry['id']}: missing #{field} path #{path}"
        end
      end
    end

    def validate_assets(assets, label)
      return ["#{label}: assets must be an array"] unless assets.is_a?(Array)
      assets.each_with_object([]) do |path, errors|
        absolute = File.expand_path(path.to_s, File.dirname(@manifest_path))
        manual_root = File.dirname(@manifest_path)
        unless safe_relative?(path) && path.start_with?("assets/") && within?(absolute, manual_root) && File.file?(absolute) && within?(File.realpath(absolute), File.realpath(manual_root))
          errors << "#{label}: missing asset #{path}"
        end
      end
    end

    def validate_document_references(document, label)
      File.read(document).scan(/!?\[[^\]]*\]\(([^)]+)\)/).flatten.each_with_object([]) do |raw, errors|
        target = raw.to_s.strip.split(/[?#]/, 2).first
        next if target.empty? || target.start_with?("#", "/") || target.match?(/\A[a-z][a-z0-9+.-]*:/i)
        absolute = File.expand_path(target, File.dirname(document))
        unless within?(absolute, @repository_root) && File.exist?(absolute) && within?(File.realpath(absolute), File.realpath(@repository_root))
          errors << "#{label}: missing or unsafe document reference #{raw}"
        end
      end
    end

    def duplicates(rows, field)
      rows.grep(Hash).group_by { |row| row[field] }.select { |value, matches| value && matches.length > 1 }.keys
    end

    def safe_relative?(value)
      value.is_a?(String) && !value.empty? && !Pathname.new(value).absolute? && Pathname.new(value).each_filename.none? { |part| part == ".." }
    end

    def within?(path, root)
      path == root || path.start_with?(root + File::SEPARATOR)
    end

    def blank?(value)
      value.nil? || (value.respond_to?(:empty?) && value.empty?)
    end
  end
end

#!/usr/bin/env ruby

require "fileutils"
require "json"
require "open3"
require "pathname"
require "tmpdir"

module ManualDocs
  class ReleaseInventoryError < StandardError; end

  class DetachedReleaseInventory
    INIT_SCRIPT = <<~'GROOVY'
      import groovy.json.JsonOutput

      gradle.projectsLoaded {
          def releaseRoot = gradle.rootProject
          releaseRoot.tasks.register("exportReleaseManualModuleInventory") {
              doLast {
                  def rootPath = releaseRoot.projectDir.toPath()
                  def rows = releaseRoot.allprojects
                      .findAll { project -> project != releaseRoot && project.name != "buildSrc" }
                      .collect { project ->
                          def sourceDir = rootPath.relativize(project.projectDir.toPath()).toString().replace('\\', '/')
                          def kind = sourceDir.startsWith("benchmark/") ? "benchmark" :
                              (sourceDir.startsWith("examples/") ? "example" : "library")
                          [gradlePath: project.path, projectName: project.name, sourceDir: sourceDir, kind: kind]
                      }
                      .sort { left, right -> left.gradlePath <=> right.gradlePath }
                  def output = new File(System.getProperty("manual.inventory.output"))
                  output.parentFile.mkdirs()
                  output.setText(JsonOutput.prettyPrint(JsonOutput.toJson(rows)) + "\n", "UTF-8")
              }
          }
      }
    GROOVY

    def initialize(repository_root:)
      @repository_root = File.expand_path(repository_root)
    end

    def call(sha)
      parent = Dir.mktmpdir("graph-release-inventory-")
      checkout = File.join(parent, "release")
      init_script = File.join(parent, "release-inventory.gradle")
      output = File.join(parent, "release-module-inventory.json")
      added = false
      File.write(init_script, INIT_SCRIPT)
      _stdout, stderr, status = Open3.capture3("git", "-C", @repository_root, "worktree", "add", "--detach", "--quiet", checkout, sha)
      raise ReleaseInventoryError, "temporary release worktree could not be created: #{stderr.strip}" unless status.success?
      added = true
      command = [File.join(checkout, "gradlew"), "--no-configuration-cache", "--console=plain",
                 "-Dmanual.inventory.output=#{output}", "-I", init_script, "exportReleaseManualModuleInventory"]
      _stdout, stderr, status = Open3.capture3(*command, chdir: checkout)
      unless status.success?
        detail = stderr.lines.last(20).join.strip
        raise ReleaseInventoryError, "release Gradle inventory export failed at #{sha}: #{detail}"
      end
      raise ReleaseInventoryError, "release Gradle inventory export produced no JSON" unless File.file?(output)
      JSON.parse(File.read(output))
    rescue JSON::ParserError => error
      raise ReleaseInventoryError, "release Gradle inventory JSON is invalid: #{error.message}"
    ensure
      Open3.capture3("git", "-C", @repository_root, "worktree", "remove", "--force", checkout) if added
      FileUtils.remove_entry(parent) if parent && File.exist?(parent)
      Open3.capture3("git", "-C", @repository_root, "worktree", "prune") if @repository_root
    end
  end

  class ReleaseInventory
    VALID_KINDS = %w[library benchmark example].freeze

    def self.parse_cli(arguments)
      case arguments.length
      when 4
        tag, sha, output, count = arguments
        { tag: tag, sha: sha, output: output, count: count, legacy_input: nil }
      when 5
        tag, sha, legacy_input, output, count = arguments
        { tag: tag, sha: sha, output: output, count: count, legacy_input: legacy_input }
      else
        raise ArgumentError, "usage: ruby scripts/manual/release_inventory.rb TAG EXPECTED_SHA [LEGACY_INPUT] OUTPUT EXPECTED_COUNT"
      end
    end

    def initialize(repository_root:, tag:, expected_sha:, output_path:, expected_count:,
                   expected_kinds: { "library" => 15, "benchmark" => 4, "example" => 12 }, git_runner: nil,
                   inventory_exporter: nil)
      @repository_root = File.expand_path(repository_root)
      @tag = tag
      @expected_sha = expected_sha
      @output_path = output_path
      @expected_count = Integer(expected_count)
      @expected_kinds = expected_kinds
      @git_runner = git_runner || method(:run_git)
      @inventory_exporter = inventory_exporter || DetachedReleaseInventory.new(repository_root: @repository_root)
    end

    def write
      type, found = @git_runner.call(["cat-file", "-t", "refs/tags/#{@tag}"])
      raise ReleaseInventoryError, "release tag not found: #{@tag}" unless found
      raise ReleaseInventoryError, "release tag must be annotated: #{@tag}" unless type.strip == "tag"

      sha, found = @git_runner.call(["rev-parse", "--verify", "refs/tags/#{@tag}^{commit}"])
      raise ReleaseInventoryError, "release tag not found: #{@tag}" unless found
      sha = sha.strip
      raise ReleaseInventoryError, "release tag #{@tag} resolves to #{sha}, expected #{@expected_sha}" unless sha.casecmp?(@expected_sha)

      rows = @inventory_exporter.call(sha)
      raise ReleaseInventoryError, "module inventory must be an array" unless rows.is_a?(Array)

      validate_rows(rows)
      rows = rows.sort_by { |row| row.fetch("gradlePath") }
      FileUtils.mkdir_p(File.dirname(@output_path))
      File.binwrite(@output_path, JSON.pretty_generate(rows.map { |row| sort_keys(row) }) + "\n")
      rows
    rescue JSON::ParserError => error
      raise ReleaseInventoryError, "module inventory JSON is invalid: #{error.message}"
    end

    private

    def validate_rows(rows)
      raise ReleaseInventoryError, "release inventory count #{rows.length}, expected #{@expected_count}" unless rows.length == @expected_count
      duplicates = rows.group_by { |row| row["gradlePath"] }.select { |_path, matches| matches.length > 1 }.keys
      raise ReleaseInventoryError, "duplicate gradlePath: #{duplicates.sort.join(', ')}" unless duplicates.empty?

      rows.each do |row|
        %w[gradlePath projectName sourceDir kind].each { |field| raise ReleaseInventoryError, "missing #{field}" unless row[field].is_a?(String) && !row[field].empty? }
        source = row.fetch("sourceDir")
        raise ReleaseInventoryError, "unsafe sourceDir: #{source}" unless safe_relative?(source)
        raise ReleaseInventoryError, "invalid kind: #{row['kind']}" unless VALID_KINDS.include?(row["kind"])
      end
      actual = rows.group_by { |row| row.fetch("kind") }.transform_values(&:length)
      raise ReleaseInventoryError, "release inventory classification #{actual}, expected #{@expected_kinds}" unless actual == @expected_kinds
    end

    def safe_relative?(value)
      path = Pathname.new(value)
      !path.absolute? && path.each_filename.none? { |part| part == ".." }
    end

    def sort_keys(hash)
      hash.keys.sort.each_with_object({}) { |key, result| result[key] = hash[key] }
    end

    def run_git(arguments)
      stdout, _stderr, status = Open3.capture3("git", "-C", @repository_root, *arguments)
      [stdout, status.success?]
    end
  end
end

if $PROGRAM_NAME == __FILE__
  begin
    arguments = ManualDocs::ReleaseInventory.parse_cli(ARGV)
  rescue ArgumentError => error
    abort(error.message)
  end
  warn("Ignoring legacy working-tree inventory argument #{arguments[:legacy_input]}; the detached release export is authoritative.") if arguments[:legacy_input]
  rows = ManualDocs::ReleaseInventory.new(repository_root: Dir.pwd, tag: arguments.fetch(:tag), expected_sha: arguments.fetch(:sha),
    output_path: arguments.fetch(:output), expected_count: arguments.fetch(:count)).write
  counts = rows.group_by { |row| row.fetch("kind") }.transform_values(&:length)
  puts "Release inventory written: #{rows.length} projects (#{counts['library']} libraries, #{counts['benchmark']} benchmarks, #{counts['example']} examples)."
end

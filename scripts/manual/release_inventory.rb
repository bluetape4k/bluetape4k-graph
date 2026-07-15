#!/usr/bin/env ruby

require "fileutils"
require "json"
require "open3"
require "pathname"

module ManualDocs
  class ReleaseInventoryError < StandardError; end

  class ReleaseInventory
    VALID_KINDS = %w[library benchmark example].freeze

    def initialize(repository_root:, tag:, expected_sha:, inventory_path:, output_path:, expected_count:,
                   expected_kinds: { "library" => 15, "benchmark" => 4, "example" => 12 }, git_runner: nil)
      @repository_root = File.expand_path(repository_root)
      @tag = tag
      @expected_sha = expected_sha
      @inventory_path = inventory_path
      @output_path = output_path
      @expected_count = Integer(expected_count)
      @expected_kinds = expected_kinds
      @git_runner = git_runner || method(:run_git)
    end

    def write
      type, found = @git_runner.call(["cat-file", "-t", "refs/tags/#{@tag}"])
      raise ReleaseInventoryError, "release tag not found: #{@tag}" unless found
      raise ReleaseInventoryError, "release tag must be annotated: #{@tag}" unless type.strip == "tag"

      sha, found = @git_runner.call(["rev-parse", "--verify", "refs/tags/#{@tag}^{commit}"])
      raise ReleaseInventoryError, "release tag not found: #{@tag}" unless found
      sha = sha.strip
      raise ReleaseInventoryError, "release tag #{@tag} resolves to #{sha}, expected #{@expected_sha}" unless sha.casecmp?(@expected_sha)

      tree_output, found = @git_runner.call(["ls-tree", "-r", "--name-only", sha])
      raise ReleaseInventoryError, "release tree could not be read: #{sha}" unless found
      tree = tree_output.lines(chomp: true).to_h { |path| [path, true] }
      rows = JSON.parse(File.read(@inventory_path))
      raise ReleaseInventoryError, "module inventory must be an array" unless rows.is_a?(Array)

      validate_rows(rows, tree)
      rows = rows.sort_by { |row| row.fetch("gradlePath") }
      FileUtils.mkdir_p(File.dirname(@output_path))
      File.binwrite(@output_path, JSON.pretty_generate(rows.map { |row| sort_keys(row) }) + "\n")
      rows
    rescue JSON::ParserError => error
      raise ReleaseInventoryError, "module inventory JSON is invalid: #{error.message}"
    end

    private

    def validate_rows(rows, tree)
      raise ReleaseInventoryError, "release inventory count #{rows.length}, expected #{@expected_count}" unless rows.length == @expected_count
      duplicates = rows.group_by { |row| row["gradlePath"] }.select { |_path, matches| matches.length > 1 }.keys
      raise ReleaseInventoryError, "duplicate gradlePath: #{duplicates.sort.join(', ')}" unless duplicates.empty?

      rows.each do |row|
        %w[gradlePath projectName sourceDir kind].each { |field| raise ReleaseInventoryError, "missing #{field}" unless row[field].is_a?(String) && !row[field].empty? }
        source = row.fetch("sourceDir")
        raise ReleaseInventoryError, "unsafe sourceDir: #{source}" unless safe_relative?(source)
        raise ReleaseInventoryError, "invalid kind: #{row['kind']}" unless VALID_KINDS.include?(row["kind"])
        build_file = "#{source}/build.gradle.kts"
        raise ReleaseInventoryError, "sourceDir not present in release tree: #{source}" unless tree[build_file]
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
  abort("usage: ruby scripts/manual/release_inventory.rb TAG EXPECTED_SHA INPUT OUTPUT EXPECTED_COUNT") unless ARGV.length == 5
  tag, sha, input, output, count = ARGV
  rows = ManualDocs::ReleaseInventory.new(repository_root: Dir.pwd, tag: tag, expected_sha: sha,
    inventory_path: input, output_path: output, expected_count: count).write
  counts = rows.group_by { |row| row.fetch("kind") }.transform_values(&:length)
  puts "Release inventory written: #{rows.length} projects (#{counts['library']} libraries, #{counts['benchmark']} benchmarks, #{counts['example']} examples)."
end

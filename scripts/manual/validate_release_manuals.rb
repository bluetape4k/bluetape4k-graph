#!/usr/bin/env ruby

require "json"
require_relative "manual_contract"
require_relative "release_contract"

module ManualDocs
  class ReleaseManualValidator
    def initialize(repository_root:, tag:, expected_sha:, inventory_path:, manifest_path:, release_contract: nil)
      @repository_root = File.expand_path(repository_root)
      @tag = tag
      @expected_sha = expected_sha
      @inventory_path = File.expand_path(inventory_path, @repository_root)
      @manifest_path = File.expand_path(manifest_path, @repository_root)
      @release_contract = release_contract || ReleaseContract.new(
        repository_root: @repository_root,
        tag: @tag,
        expected_sha: @expected_sha,
      )
    end

    def errors
      release_result = @release_contract.validate
      errors = release_result.errors.dup
      return errors << "release inventory not found: #{@inventory_path}" unless File.file?(@inventory_path)
      return errors << "manual manifest not found: #{@manifest_path}" unless File.file?(@manifest_path)

      inventory = JSON.parse(File.read(@inventory_path))
      errors.concat(Validator.new(
        inventory: inventory,
        manifest_path: @manifest_path,
        repository_root: @repository_root,
        expected_release: { "ref" => @tag, "commit" => @expected_sha },
        strict: true,
      ).errors)
      errors.sort
    rescue JSON::ParserError => error
      errors << "release inventory JSON is invalid: #{error.message}"
    end

    def checked_link_count
      @release_contract.validate.checked_count
    end
  end
end

if $PROGRAM_NAME == __FILE__
  tag = ARGV.fetch(0, "0.5.1")
  sha = ARGV.fetch(1, "3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907")
  inventory_path = ARGV.fetch(2, "build/manual/release-module-inventory.json")
  validator = ManualDocs::ReleaseManualValidator.new(
    repository_root: Dir.pwd,
    tag: tag,
    expected_sha: sha,
    inventory_path: inventory_path,
    manifest_path: "docs/manual/manifest.yaml",
  )
  errors = validator.errors
  abort(errors.join("\n")) unless errors.empty?
  puts "Strict release manual contract valid: annotated tag #{tag} -> #{sha}; #{validator.checked_link_count} source links checked."
end

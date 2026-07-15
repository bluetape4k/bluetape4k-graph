#!/usr/bin/env ruby

require "json"
require_relative "manual_contract"
require_relative "release_contract"

tag = ARGV.fetch(0, "0.5.1")
sha = ARGV.fetch(1, "3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907")
inventory_path = ARGV.fetch(2, "build/manual/release-module-inventory.json")

release = ManualDocs::ReleaseContract.new(repository_root: Dir.pwd, tag: tag, expected_sha: sha).validate
errors = release.errors
if File.file?(inventory_path)
  errors += ManualDocs::Validator.new(
    inventory: JSON.parse(File.read(inventory_path)),
    manifest_path: "docs/manual/manifest.yaml",
    repository_root: Dir.pwd,
    expected_release: { "ref" => tag, "commit" => sha },
    strict: false,
  ).errors
end
abort(errors.sort.join("\n")) unless errors.empty?
puts "Release manual contract valid: annotated tag #{tag} -> #{sha}; #{release.checked_count} source links checked."

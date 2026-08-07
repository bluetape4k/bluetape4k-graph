#!/usr/bin/env ruby

require "json"
require_relative "manual_contract"

inventory_path = ARGV.fetch(0, "build/manual/release-module-inventory.json")
expected_release = {
  "ref" => ENV.fetch("MANUAL_RELEASE_REF", "0.6.0"),
  "commit" => ENV.fetch("MANUAL_RELEASE_COMMIT", "72c0256e2e1cf61101d29852210e3c827ca93bc0"),
}
validator = ManualDocs::Validator.new(
  inventory: JSON.parse(File.read(inventory_path)),
  manifest_path: "docs/manual/manifest.yaml",
  repository_root: Dir.pwd,
  expected_release: expected_release,
  strict: ENV["MANUAL_STRICT"] == "1",
)
abort(validator.errors.join("\n")) unless validator.errors.empty?
mode = if ENV["MANUAL_STRICT"] == "1"
         "strict final mode"
       else
         "partial inventory-only mode; routes, sourcePaths, and assets are not required until content integration"
       end
puts "Manual contract valid (#{mode})."

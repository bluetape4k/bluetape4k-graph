#!/usr/bin/env ruby

require "json"
require "fileutils"
require "yaml"

inventory_path = ARGV.fetch(0, "build/manual/release-module-inventory.json")
output_path = ARGV.fetch(1, "docs/manual/manifest.yaml")
rows = JSON.parse(File.read(inventory_path))

modules = rows.map do |row|
  kind = row.fetch("kind")
  {
    "id" => row.fetch("projectName"),
    "gradlePath" => row.fetch("gradlePath"),
    "projectName" => row.fetch("projectName"),
    "sourceDir" => row.fetch("sourceDir"),
    "kind" => kind,
    "artifact" => kind == "library" ? "io.github.bluetape4k.graph:#{row.fetch('projectName')}" : nil,
    "status" => "stable",
  }
end.sort_by { |row| row.fetch("id") }

manifest = {
  "schemaVersion" => 2,
  "repository" => "bluetape4k/bluetape4k-graph",
  "releaseRef" => "0.5.1",
  "releaseCommit" => "3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907",
  "publication" => {
    "manualVersion" => "0.5",
    "sourceRoot" => "docs/manual",
    "locales" => %w[en ko],
    "contentStatus" => "inventory-only",
  },
  "modules" => modules,
}

FileUtils.mkdir_p(File.dirname(output_path))
File.write(output_path, YAML.dump(manifest).each_line.map(&:rstrip).join("\n") + "\n")
puts "Graph manual manifest written: #{modules.length} projects."

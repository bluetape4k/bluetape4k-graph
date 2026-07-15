#!/usr/bin/env ruby

require "json"
require "fileutils"
require "yaml"

inventory_path = ARGV.fetch(0, "build/manual/release-module-inventory.json")
output_path = ARGV.fetch(1, "docs/manual/manifest.yaml")
rows = JSON.parse(File.read(inventory_path))

overview_documents = %w[
  index.md
  getting-started.md
  architecture/repository-map.md
  architecture/core-model.md
  architecture/paired-apis.md
  architecture/schema-and-transactions.md
  backends/selection-guide.md
  backends/neo4j-and-memgraph.md
  backends/apache-age.md
  backends/tinkerpop.md
  backends/falkordb.md
  graph-io/formats.md
  graph-io/execution-model.md
  graph-io/okio-security.md
  frameworks/spring-boot.md
  frameworks/ktor.md
  guides/learning-path.md
  guides/testing.md
  guides/operations.md
  guides/failure-and-cancellation.md
  guides/benchmark-based-selection.md
  benchmarks/overview.md
].freeze

assets = %w[
  assets/overview/repository-learning-map.svg
  assets/overview/repository-learning-map.png
  assets/architecture/core-abstraction-map.svg
  assets/architecture/core-abstraction-map.png
  assets/backends/backend-decision-map.svg
  assets/backends/backend-decision-map.png
  assets/graph-io/graph-io-pipeline.svg
  assets/graph-io/graph-io-pipeline.png
  assets/frameworks/framework-integration-flow.svg
  assets/frameworks/framework-integration-flow.png
].freeze

benchmark_routes = {
  "graph-benchmark" => "benchmarks/graph-operations.md",
  "graph-io-benchmark" => "benchmarks/graph-io.md",
  "graph-age-benchmark" => "benchmarks/age-and-neo4j.md",
  "graph-neo4j-benchmark" => "benchmarks/age-and-neo4j.md",
}.freeze

def route_for(row, benchmark_routes)
  id = row.fetch("projectName")
  case row.fetch("kind")
  when "library"
    slug = id == "bluetape4k-graph-okio" ? "graph-okio" : id
    "modules/#{slug}.md"
  when "example"
    "examples/#{id.delete_suffix('-examples')}.md"
  when "benchmark"
    benchmark_routes.fetch(id)
  end
end

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
    "sourcePaths" => [row.fetch("sourceDir")],
    "routes" => {
      "en" => "en/#{route_for(row, benchmark_routes)}",
      "ko" => "ko/#{route_for(row, benchmark_routes)}",
    },
  }
end.sort_by { |row| row.fetch("id") }

manifest = {
  "schemaVersion" => 2,
  "repository" => "bluetape4k-graph",
  "stableVersion" => "0.5.1",
  "stableMinor" => "0.5",
  "releaseTag" => "0.5.1",
  "releaseRef" => "0.5.1",
  "releaseCommit" => "3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907",
  "publication" => {
    "manualVersion" => "0.5",
    "sourceRoot" => "docs/manual",
    "locales" => %w[en ko],
    "contentStatus" => "complete",
  },
  "overview" => {
    "documents" => {
      "en" => overview_documents.map { |path| "en/#{path}" },
      "ko" => overview_documents.map { |path| "ko/#{path}" },
    },
    "assets" => assets,
  },
  "modules" => modules,
}

FileUtils.mkdir_p(File.dirname(output_path))
File.write(output_path, YAML.dump(manifest).each_line.map(&:rstrip).join("\n") + "\n")
puts "Graph manual manifest written: #{modules.length} projects."

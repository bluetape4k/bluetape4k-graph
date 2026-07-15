require "json"
require "minitest/autorun"
require "open3"
require "tmpdir"
require "yaml"

class BuildGraphManifestTest < Minitest::Test
  def test_exports_locale_routes_with_the_common_schema_v2_fields
    Dir.mktmpdir do |root|
      inventory = File.join(root, "inventory.json")
      manifest = File.join(root, "manifest.yaml")
      File.write(inventory, JSON.generate([
        {
          "gradlePath" => ":bluetape4k-graph-core",
          "projectName" => "bluetape4k-graph-core",
          "sourceDir" => "graph/graph-core",
          "kind" => "library",
        },
      ]))

      script = File.expand_path("build_graph_manifest.rb", __dir__)
      _stdout, stderr, status = Open3.capture3("ruby", script, inventory, manifest)

      assert status.success?, stderr
      row = YAML.safe_load(File.read(manifest)).fetch("modules").first
      assert_equal "foundation", row.fetch("group")
      assert_equal "en/modules/bluetape4k-graph-core.md", row.fetch("en")
      assert_equal "ko/modules/bluetape4k-graph-core.md", row.fetch("ko")
      refute row.key?("routes")
    end
  end

  def test_classifies_every_manual_navigation_group
    Dir.mktmpdir do |root|
      inventory = File.join(root, "inventory.json")
      manifest = File.join(root, "manifest.yaml")
      File.write(inventory, JSON.generate([
        row(":bluetape4k-graph-core", "bluetape4k-graph-core", "graph/graph-core", "library"),
        row(":bluetape4k-graph-neo4j", "bluetape4k-graph-neo4j", "graph/neo4j", "library"),
        row(":bluetape4k-graph-io-core", "bluetape4k-graph-io-core", "graph-io/core", "library"),
        row(":bluetape4k-graph-spring-boot", "bluetape4k-graph-spring-boot", "spring-boot/graph", "library"),
        row(":graph-benchmark", "graph-benchmark", "benchmark/graph", "benchmark"),
        row(":graph-examples", "graph-examples", "examples/graph", "example"),
      ]))

      script = File.expand_path("build_graph_manifest.rb", __dir__)
      _stdout, stderr, status = Open3.capture3("ruby", script, inventory, manifest)

      assert status.success?, stderr
      groups = YAML.safe_load(File.read(manifest)).fetch("modules").to_h { |entry| [entry.fetch("id"), entry.fetch("group")] }
      assert_equal "foundation", groups.fetch("bluetape4k-graph-core")
      assert_equal "backends", groups.fetch("bluetape4k-graph-neo4j")
      assert_equal "graph-io", groups.fetch("bluetape4k-graph-io-core")
      assert_equal "frameworks", groups.fetch("bluetape4k-graph-spring-boot")
      assert_equal "benchmarks", groups.fetch("graph-benchmark")
      assert_equal "examples", groups.fetch("graph-examples")
    end
  end

  private

  def row(gradle_path, project_name, source_dir, kind)
    {
      "gradlePath" => gradle_path,
      "projectName" => project_name,
      "sourceDir" => source_dir,
      "kind" => kind,
    }
  end
end

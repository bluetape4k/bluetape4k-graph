require "json"
require "fileutils"
require "minitest/autorun"
require "tmpdir"

require_relative "manual_contract"

class ManualContractTest < Minitest::Test
  RELEASE = { "ref" => "0.5.1", "commit" => "3" * 40 }.freeze

  def validate(module_overrides = {}, manifest_overrides = {}, strict: false)
    Dir.mktmpdir do |root|
      source = File.join(root, "graph/core")
      FileUtils.mkdir_p(source)
      File.write(File.join(source, "build.gradle.kts"), "")
      row = { "id" => "core", "gradlePath" => ":core", "projectName" => "core", "sourceDir" => "graph/core",
              "kind" => "library", "artifact" => "g:core", "status" => "stable" }.merge(module_overrides)
      manifest = { "schemaVersion" => 2, "repository" => "o/r", "releaseRef" => RELEASE["ref"],
                   "releaseCommit" => RELEASE["commit"], "publication" => { "manualVersion" => "0.5", "locales" => %w[en ko] },
                   "modules" => [row] }.merge(manifest_overrides)
      path = File.join(root, "docs/manual/manifest.yaml")
      FileUtils.mkdir_p(File.dirname(path))
      File.write(path, YAML.dump(manifest))
      yield ManualDocs::Validator.new(inventory: [row.slice("gradlePath", "projectName", "sourceDir", "kind")], manifest_path: path,
        repository_root: root, expected_release: RELEASE, strict: strict)
    end
  end

  def test_accepts_inventory_only_partial_manifest
    validate { |validator| assert_empty validator.errors }
  end

  def test_rejects_missing_locale_route_when_routes_are_declared
    validate("routes" => { "en" => "en/modules/core.md" }) { |validator| assert validator.errors.any? { |e| e.include?("missing Korean route") } }
  end

  def test_rejects_missing_source_file
    validate("sourcePaths" => ["graph/core/Missing.kt"]) { |validator| assert validator.errors.any? { |e| e.include?("missing sourcePaths") } }
  end

  def test_rejects_release_mismatch
    validate({}, "releaseRef" => "0.5.0") { |validator| assert validator.errors.any? { |e| e.include?("releaseRef must be 0.5.1") } }
  end

  def test_strict_mode_requires_routes_and_source_paths
    validate({}, {}, strict: true) do |validator|
      assert validator.errors.any? { |e| e.include?("routes must be a mapping") }
      assert validator.errors.any? { |e| e.include?("missing manifest field sourcePaths") }
    end
  end
end

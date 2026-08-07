require "json"
require "fileutils"
require "minitest/autorun"
require "tmpdir"

require_relative "manual_contract"

class ManualContractTest < Minitest::Test
  RELEASE = { "ref" => "0.6.0", "commit" => "3" * 40 }.freeze

  def validate(module_overrides = {}, manifest_overrides = {}, strict: false)
    Dir.mktmpdir do |root|
      source = File.join(root, "graph/core")
      FileUtils.mkdir_p(source)
      File.write(File.join(source, "build.gradle.kts"), "")
      row = { "id" => "core", "gradlePath" => ":core", "projectName" => "core", "sourceDir" => "graph/core",
              "kind" => "library", "group" => "foundation", "artifact" => "g:core", "status" => "stable" }.merge(module_overrides)
      manifest = { "schemaVersion" => 2, "repository" => "bluetape4k-graph", "releaseRef" => RELEASE["ref"],
                   "stableVersion" => RELEASE["ref"], "stableMinor" => "0.6", "releaseTag" => RELEASE["ref"],
                   "releaseCommit" => RELEASE["commit"], "publication" => { "manualVersion" => "0.6", "locales" => %w[en ko] },
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
    validate("en" => "en/modules/core.md") { |validator| assert validator.errors.any? { |e| e.include?("missing Korean route") } }
  end

  def test_rejects_missing_source_file
    validate("sourcePaths" => ["graph/core/Missing.kt"]) { |validator| assert validator.errors.any? { |e| e.include?("missing sourcePaths") } }
  end

  def test_rejects_release_mismatch
    validate({}, "releaseRef" => "0.5.0") { |validator| assert validator.errors.any? { |e| e.include?("releaseRef must be 0.6.0") } }
  end

  def test_rejects_missing_blank_or_unknown_module_group
    validate({ "group" => nil }) { |validator| assert validator.errors.any? { |e| e.include?("missing manifest field group") } }
    validate({ "group" => "" }) { |validator| assert validator.errors.any? { |e| e.include?("invalid group") } }
    validate({ "group" => "other" }) { |validator| assert validator.errors.any? { |e| e.include?("invalid group") } }
  end

  def test_rejects_missing_canonical_release_header
    validate({}, { "repository" => "bluetape4k/bluetape4k-graph", "stableVersion" => nil, "stableMinor" => nil, "releaseTag" => nil }) do |validator|
      assert validator.errors.any? { |e| e.include?("repository must be bluetape4k-graph") }
      assert validator.errors.any? { |e| e.include?("stableVersion must be 0.6.0") }
      assert validator.errors.any? { |e| e.include?("stableMinor must be 0.6") }
      assert validator.errors.any? { |e| e.include?("releaseTag must be 0.6.0") }
    end
  end

  def test_strict_mode_requires_routes_and_source_paths
    validate({}, {}, strict: true) do |validator|
      assert validator.errors.any? { |e| e.include?("missing English route") }
      assert validator.errors.any? { |e| e.include?("missing Korean route") }
      assert validator.errors.any? { |e| e.include?("missing manifest field sourcePaths") }
    end
  end

  def test_strict_mode_requires_exact_source_dir_ownership
    validate({ "sourcePaths" => [] }, {}, strict: true) do |validator|
      assert validator.errors.any? { |e| e.include?("sourcePaths must equal [sourceDir]") }
    end
    validate({ "sourcePaths" => ["graph/neo4j"] }, {}, strict: true) do |validator|
      assert validator.errors.any? { |e| e.include?("sourcePaths must equal [sourceDir]") }
    end
  end

  def test_strict_mode_requires_complete_publication_header
    validate({}, {
      "publication" => { "manualVersion" => "0.4", "sourceRoot" => "manual", "locales" => %w[en ko], "contentStatus" => "inventory-only" },
    }, strict: true) do |validator|
      assert validator.errors.any? { |e| e.include?("manualVersion must be 0.6") }
      assert validator.errors.any? { |e| e.include?("sourceRoot must be docs/manual") }
      assert validator.errors.any? { |e| e.include?("contentStatus must be complete") }
    end
  end

  def test_rejects_non_mapping_publication_in_all_modes
    [nil, "complete", []].each do |publication|
      [false, true].each do |strict|
        validate({}, { "publication" => publication }, strict: strict) do |validator|
          assert_includes validator.errors, "manual publication must be a mapping"
        end
      end
    end
  end

  def test_strict_mode_rejects_missing_declared_asset
    validate({
      "en" => "en/modules/core.md", "ko" => "ko/modules/core.md",
      "sourcePaths" => ["graph/core/build.gradle.kts"],
      "assets" => ["assets/missing.svg"],
    }, {}, strict: true) do |validator|
      assert validator.errors.any? { |e| e.include?("missing asset assets/missing.svg") }
    end
  end


  def test_strict_mode_requires_complete_locale_matched_overview_documents
    validate({
      "en" => "en/modules/core.md", "ko" => "ko/modules/core.md",
      "sourcePaths" => ["graph/core/build.gradle.kts"],
    }, {
      "overview" => { "documents" => { "en" => ["en/index.md"], "ko" => ["ko/getting-started.md"] }, "assets" => [] },
    }, strict: true) do |validator|
      assert validator.errors.any? { |e| e.include?("overview English/Korean routes differ") }
    end
  end

  def test_strict_mode_rejects_unregistered_manual_document
    validate({
      "en" => "en/modules/core.md", "ko" => "ko/modules/core.md",
      "sourcePaths" => ["graph/core/build.gradle.kts"],
    }, {
      "overview" => { "documents" => { "en" => ["en/index.md"], "ko" => ["ko/index.md"] }, "assets" => [] },
    }, strict: true) do |validator|
      manual_root = File.dirname(validator.instance_variable_get(:@manifest_path))
      FileUtils.mkdir_p(File.join(manual_root, "en/modules"))
      FileUtils.mkdir_p(File.join(manual_root, "ko/modules"))
      File.write(File.join(manual_root, "en/modules/core.md"), "# core\n")
      File.write(File.join(manual_root, "ko/modules/core.md"), "# core\n")
      File.write(File.join(manual_root, "en/unregistered.md"), "# missing\n")
      refreshed = ManualDocs::Validator.new(
        inventory: validator.instance_variable_get(:@inventory),
        manifest_path: validator.instance_variable_get(:@manifest_path),
        repository_root: validator.instance_variable_get(:@repository_root),
        expected_release: RELEASE,
        strict: true,
      )
      assert refreshed.errors.any? { |e| e.include?("unregistered manual document en/unregistered.md") }
    end
  end

  def test_strict_mode_allows_two_projects_to_share_one_benchmark_route
    Dir.mktmpdir do |root|
      manual_root = File.join(root, "docs/manual")
      %w[en ko].each do |locale|
        FileUtils.mkdir_p(File.join(manual_root, locale, "benchmarks"))
        File.write(File.join(manual_root, locale, "index.md"), "# index\n")
        File.write(File.join(manual_root, locale, "benchmarks/age-and-neo4j.md"), "# comparison\n")
      end
      rows = [
        { "id" => "age", "gradlePath" => ":age", "projectName" => "age", "sourceDir" => "benchmark/age", "kind" => "benchmark", "group" => "benchmarks", "artifact" => nil, "status" => "stable" },
        { "id" => "neo4j", "gradlePath" => ":neo4j", "projectName" => "neo4j", "sourceDir" => "benchmark/neo4j", "kind" => "benchmark", "group" => "benchmarks", "artifact" => nil, "status" => "stable" },
      ]
      rows.each { |row| FileUtils.mkdir_p(File.join(root, row.fetch("sourceDir"))) }
      modules = rows.map do |row|
        row.merge("sourcePaths" => [row.fetch("sourceDir")],
          "en" => "en/benchmarks/age-and-neo4j.md", "ko" => "ko/benchmarks/age-and-neo4j.md")
      end
      manifest = {
        "schemaVersion" => 2, "repository" => "bluetape4k-graph", "releaseRef" => RELEASE["ref"],
        "stableVersion" => RELEASE["ref"], "stableMinor" => "0.6", "releaseTag" => RELEASE["ref"],
        "releaseCommit" => RELEASE["commit"], "publication" => {
          "manualVersion" => "0.6", "sourceRoot" => "docs/manual", "locales" => %w[en ko], "contentStatus" => "complete",
        },
        "overview" => { "documents" => { "en" => ["en/index.md"], "ko" => ["ko/index.md"] }, "assets" => [] },
        "modules" => modules,
      }
      path = File.join(manual_root, "manifest.yaml")
      File.write(path, YAML.dump(manifest))
      inventory = rows.map { |row| row.slice("gradlePath", "projectName", "sourceDir", "kind") }
      validator = ManualDocs::Validator.new(inventory: inventory, manifest_path: path, repository_root: root,
        expected_release: RELEASE, strict: true)
      assert_empty validator.errors
    end
  end
end

require "json"
require "minitest/autorun"
require "tmpdir"

require_relative "release_inventory"

class ReleaseInventoryTest < Minitest::Test
  SHA = "3" * 40

  def row(path: ":core", source: "graph/core", kind: "library")
    { "gradlePath" => path, "projectName" => path.delete_prefix(":"), "sourceDir" => source, "kind" => kind }
  end

  def runner(type: "tag", sha: SHA, tree: ["graph/core/build.gradle.kts"])
    lambda do |args|
      case args.first
      when "cat-file" then ["#{type}\n", true]
      when "rev-parse" then ["#{sha}\n", true]
      when "ls-tree" then [tree.join("\n") + "\n", true]
      else ["", false]
      end
    end
  end

  def build(rows, **options)
    Dir.mktmpdir do |root|
      output = File.join(root, "output.json")
      return yield ManualDocs::ReleaseInventory.new(repository_root: root, tag: "0.6.0", expected_sha: SHA,
        output_path: output, expected_count: options.fetch(:expected_count, rows.length),
        expected_kinds: options.fetch(:expected_kinds, { "library" => rows.length }),
        inventory_exporter: options.fetch(:inventory_exporter, ->(_sha) { rows }),
        git_runner: options.fetch(:git_runner, runner))
    end
  end

  def test_parses_canonical_four_argument_cli
    parsed = ManualDocs::ReleaseInventory.parse_cli(%w[0.6.0 abc output.json 31])
    assert_equal({ tag: "0.6.0", sha: "abc", output: "output.json", count: "31", legacy_input: nil }, parsed)
  end

  def test_preserves_documented_five_argument_cli_as_legacy_compatibility
    parsed = ManualDocs::ReleaseInventory.parse_cli(%w[0.6.0 abc ignored-input.json output.json 31])
    assert_equal "ignored-input.json", parsed.fetch(:legacy_input)
    assert_equal "output.json", parsed.fetch(:output)
  end


  def test_uses_release_export_instead_of_working_tree_inventory
    release_row = row(path: ":release", source: "graph/release")
    exporter = ->(_sha) { [release_row] }
    build([row(path: ":head", source: "graph/head")], inventory_exporter: exporter,
      git_runner: runner(tree: ["graph/release/build.gradle.kts"])) do |inventory|
      written = inventory.write
      assert_equal [":release"], written.map { |entry| entry.fetch("gradlePath") }
    end
  end

  def test_rejects_missing_tag
    build([row], git_runner: ->(_args) { ["", false] }) { |inventory| assert_raises(ManualDocs::ReleaseInventoryError) { inventory.write } }
  end

  def test_rejects_lightweight_tag
    build([row], git_runner: runner(type: "commit")) { |inventory| assert_match(/annotated/, assert_raises(ManualDocs::ReleaseInventoryError) { inventory.write }.message) }
  end

  def test_rejects_wrong_commit
    build([row], git_runner: runner(sha: "4" * 40)) { |inventory| assert_match(/expected/, assert_raises(ManualDocs::ReleaseInventoryError) { inventory.write }.message) }
  end

  def test_rejects_unsafe_source_dir
    build([row(source: "../core")]) { |inventory| assert_match(/unsafe sourceDir/, assert_raises(ManualDocs::ReleaseInventoryError) { inventory.write }.message) }
  end

  def test_rejects_duplicate_gradle_paths
    rows = [row, row(source: "graph/other")]
    build(rows) { |inventory| assert_match(/duplicate gradlePath/, assert_raises(ManualDocs::ReleaseInventoryError) { inventory.write }.message) }
  end

  def test_rejects_wrong_count
    build([row], expected_count: 2) { |inventory| assert_match(/count 1, expected 2/, assert_raises(ManualDocs::ReleaseInventoryError) { inventory.write }.message) }
  end

  def test_rejects_wrong_classification
    build([row], expected_kinds: { "library" => 0, "example" => 1 }) { |inventory| assert_match(/classification/, assert_raises(ManualDocs::ReleaseInventoryError) { inventory.write }.message) }
  end
end

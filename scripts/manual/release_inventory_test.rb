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
      input = File.join(root, "input.json")
      output = File.join(root, "output.json")
      File.write(input, JSON.generate(rows))
      return yield ManualDocs::ReleaseInventory.new(repository_root: root, tag: "0.5.1", expected_sha: SHA,
        inventory_path: input, output_path: output, expected_count: options.fetch(:expected_count, rows.length),
        expected_kinds: options.fetch(:expected_kinds, { "library" => rows.length }),
        inventory_exporter: options.fetch(:inventory_exporter, ->(_sha) { rows }),
        git_runner: options.fetch(:git_runner, runner))
    end
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

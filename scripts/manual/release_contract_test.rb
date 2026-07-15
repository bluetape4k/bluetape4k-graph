require "fileutils"
require "minitest/autorun"
require "tmpdir"

require_relative "release_contract"
require_relative "validate_release_manuals"

class ReleaseContractTest < Minitest::Test
  SHA = "3" * 40

  def runner(tree:, type: "tag", sha: SHA)
    lambda do |args|
      case args.first
      when "cat-file" then ["#{type}\n", true]
      when "rev-parse" then ["#{sha}\n", true]
      when "ls-tree" then [tree.join("\n") + "\n", true]
      else ["", false]
      end
    end
  end

  def test_rejects_source_link_outside_release_tree
    Dir.mktmpdir do |root|
      FileUtils.mkdir_p(File.join(root, "docs/manual/en"))
      File.write(File.join(root, "docs/manual/en/index.md"), "[source](../../../graph/Missing.kt)\n")
      contract = ManualDocs::ReleaseContract.new(repository_root: root, tag: "0.5.1", expected_sha: SHA,
        git_runner: runner(tree: ["graph/Present.kt"]))
      assert contract.errors.any? { |e| e.include?("release path not found") }
    end
  end

  def test_rejects_github_source_link_with_wrong_release_commit
    Dir.mktmpdir do |root|
      FileUtils.mkdir_p(File.join(root, "docs/manual/en"))
      File.write(File.join(root, "docs/manual/en/index.md"),
        "[source](https://github.com/bluetape4k/bluetape4k-graph/blob/#{'4' * 40}/graph/Present.kt)\n")
      contract = ManualDocs::ReleaseContract.new(repository_root: root, tag: "0.5.1", expected_sha: SHA,
        git_runner: runner(tree: ["graph/Present.kt"]))
      assert contract.errors.any? { |e| e.include?("source link commit") }
    end
  end

  def test_rejects_non_annotated_tag
    contract = ManualDocs::ReleaseContract.new(repository_root: Dir.pwd, tag: "0.5.1", expected_sha: SHA,
      git_runner: runner(tree: [], type: "commit"))
    assert contract.errors.any? { |e| e.include?("annotated") }
  end

  def test_rejects_manifest_source_path_outside_release_tree
    Dir.mktmpdir do |root|
      FileUtils.mkdir_p(File.join(root, "docs/manual"))
      manifest = File.join(root, "docs/manual/manifest.yaml")
      File.write(manifest, YAML.dump("modules" => [{ "id" => "core", "sourcePaths" => ["graph/missing"] }]))
      contract = ManualDocs::ReleaseContract.new(repository_root: root, tag: "0.5.1", expected_sha: SHA,
        manifest_path: manifest, git_runner: runner(tree: ["graph/present/build.gradle.kts"]))
      assert contract.errors.any? { |e| e.include?("sourcePath not found in release tree: graph/missing") }
      assert_equal 1, contract.validate.source_path_count
    end
  end


  def test_final_validation_rejects_missing_inventory_file
    Dir.mktmpdir do |root|
      validator = ManualDocs::ReleaseManualValidator.new(
        repository_root: root,
        tag: "0.5.1",
        expected_sha: SHA,
        inventory_path: File.join(root, "missing.json"),
        manifest_path: File.join(root, "docs/manual/manifest.yaml"),
        release_contract: Struct.new(:validate).new(ManualDocs::ReleaseContract::ValidationResult.new(errors: [], checked_count: 0)),
      )
      assert validator.errors.any? { |error| error.include?("release inventory not found") }
    end
  end
end

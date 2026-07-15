require "fileutils"
require "minitest/autorun"
require "tmpdir"

require_relative "release_contract"

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

  def test_rejects_non_annotated_tag
    contract = ManualDocs::ReleaseContract.new(repository_root: Dir.pwd, tag: "0.5.1", expected_sha: SHA,
      git_runner: runner(tree: [], type: "commit"))
    assert contract.errors.any? { |e| e.include?("annotated") }
  end
end

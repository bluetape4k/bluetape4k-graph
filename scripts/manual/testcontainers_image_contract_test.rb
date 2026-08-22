require "minitest/autorun"
require "pathname"

require_relative "testcontainers_image_contract"

class TestcontainersImageContractTest < Minitest::Test
  REPOSITORY_ROOT = Pathname.new(File.expand_path("../..", __dir__)).freeze
  CATALOG_PATH = Pathname.new(
    ENV.fetch(
      "BLUETAPE4K_CATALOG_PATH",
      File.expand_path("../../../bluetape4k-dependencies/gradle/libs.versions.toml", REPOSITORY_ROOT),
    ),
  ).freeze

  def validator
    TestcontainersImageContract::Validator.new(
      repository_root: REPOSITORY_ROOT,
      catalog_path: CATALOG_PATH,
    )
  end

  def test_current_repository_satisfies_the_image_and_documentation_contract
    assert_empty validator.errors
  end

  def test_missing_catalog_is_reported_fail_closed
    errors = TestcontainersImageContract::Validator.new(
      repository_root: REPOSITORY_ROOT,
      catalog_path: REPOSITORY_ROOT.join("missing-catalog.toml"),
    ).errors
    assert errors.any? { |error| error.include?("central catalog not found") }, errors.join("\n")
  end
end

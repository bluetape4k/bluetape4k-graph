# frozen_string_literal: true

require "fileutils"
require "minitest/autorun"
require "open3"
require "tmpdir"

require_relative "validate_diagrams"

class ValidateDiagramsTest < Minitest::Test
  REPOSITORY_ROOT = File.expand_path("../..", __dir__)

  def test_same_dimension_stale_png_fails_svg_png_parity
    with_asset_pair do |root, base|
      FileUtils.cp(source_path("repository-learning-map.svg"), "#{base}.svg")
      FileUtils.cp(source_path("core-abstraction-map.png"), "#{base}.png")

      errors = validate(root)

      assert errors.any? { |error| error.include?("PNG does not match SVG render") }, errors.join("\n")
    end
  end

  def test_data_route_mismatch_is_fatal_even_when_raster_is_unchanged
    with_asset_pair do |root, base|
      svg = File.read(source_path("repository-learning-map.svg"))
      svg.sub!('data-route="350,445 390,445"', 'data-route="350,445 450,445"')
      File.write("#{base}.svg", svg)
      FileUtils.cp(source_path("repository-learning-map.png"), "#{base}.png")

      errors = validate(root)

      assert errors.any? { |error| error.include?("data-route does not match path d") }, errors.join("\n")
    end
  end

  def test_straight_connector_with_one_used_marker_is_valid
    with_asset_pair do |root, base|
      File.write("#{base}.svg", straight_svg)
      render_png("#{base}.svg", "#{base}.png")

      assert_empty validate(root)
    end
  end

  private

  def with_asset_pair
    Dir.mktmpdir do |root|
      base = File.join(root, "assets", "test")
      FileUtils.mkdir_p(File.dirname(base))
      yield root, base
    end
  end

  def validate(root)
    ManualDiagramValidation::Validator.new(root: root, assets: { "test" => "assets/test" }).errors
  end

  def render_png(svg_path, png_path)
    _stdout, stderr, status = Open3.capture3("cairosvg", svg_path, "-o", png_path, "-s", "2")
    assert status.success?, stderr
  end

  def source_path(filename)
    directory = filename.start_with?("core-") ? "architecture/core-abstraction-map" : "overview/repository-learning-map"
    extension = File.extname(filename)
    File.join(REPOSITORY_ROOT, "docs/manual/assets", "#{directory}#{extension}")
  end

  def straight_svg
    <<~SVG
      <?xml version="1.0" encoding="UTF-8"?>
      <svg xmlns="http://www.w3.org/2000/svg" width="1600" height="1040" viewBox="0 0 1600 1040">
        <title>Valid straight connector</title>
        <desc>Two cards connected by one straight path.</desc>
        <defs>
          <marker id="arrow-cyan" markerWidth="14" markerHeight="14" refX="12" refY="7" orient="auto" markerUnits="userSpaceOnUse" viewBox="0 0 14 14">
            <polygon points="0,1 14,7 0,13" fill="#8bd5ff" stroke="none"/>
          </marker>
        </defs>
        <style>
          .card-title { font-family: 'Architects Daughter', cursive; }
          .detail { font-family: 'Comic Mono', monospace; }
        </style>
        <rect width="1600" height="1040" fill="#0b1322"/>
        <rect class="card" data-card-id="a" x="100" y="400" width="300" height="200" fill="#172238" stroke="#8bd5ff"/>
        <rect class="card" data-card-id="b" x="700" y="400" width="300" height="200" fill="#172238" stroke="#8bd5ff"/>
        <path id="straight" class="connector" data-start-card="a" data-end-card="b" data-route="400,500 700,500" d="M 400 500 H 700" fill="none" stroke="#8bd5ff" stroke-width="5" marker-end="url(#arrow-cyan)"/>
      </svg>
    SVG
  end
end

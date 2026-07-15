# frozen_string_literal: true

require "minitest/autorun"
require "rexml/document"
require "tmpdir"

require_relative "render_graph_diagrams"

class RenderGraphDiagramsTest < Minitest::Test
  FailedStatus = Struct.new(:success?)

  def test_cairosvg_failure_preserves_existing_pair
    Dir.mktmpdir do |root|
      relative = "assets/demo"
      base = File.join(root, relative)
      FileUtils.mkdir_p(File.dirname(base))
      File.binwrite("#{base}.svg", "original svg")
      File.binwrite("#{base}.png", "original png")

      renderer = ManualDiagrams::Renderer.new(
        root: root,
        assets: { "demo" => relative },
        builders: { "demo" => -> { minimal_svg } },
        command_runner: ->(*) { ["", "injected CairoSVG failure", FailedStatus.new(false)] },
      )

      error = assert_raises(RuntimeError) { renderer.render("demo") }
      assert_includes error.message, "injected CairoSVG failure"
      assert_equal "original svg", File.binread("#{base}.svg")
      assert_equal "original png", File.binread("#{base}.png")
      assert_empty Dir.glob("#{base}.*.tmp")
      assert_empty Dir.glob("#{base}.*.backup")
    end
  end

  def test_card_and_connector_escape_xml_text_and_attributes
    unsafe = %(A&B <C> "quoted")
    fragment = <<~SVG
      <svg>
        #{ManualDiagrams.card(id: unsafe, x: 10, y: 20, w: 200, h: 120, color: :cyan, title: unsafe, lines: [unsafe])}
        #{ManualDiagrams.connector(id: unsafe, d: unsafe, route: unsafe, from: unsafe, to: unsafe, color: :cyan)}
      </svg>
    SVG

    document = REXML::Document.new(fragment)
    rect = REXML::XPath.first(document, ".//rect")
    title = REXML::XPath.first(document, ".//text[@class='card-title']")
    detail = REXML::XPath.first(document, ".//text[@class='detail']")
    path = REXML::XPath.first(document, ".//path")

    assert_equal unsafe, rect.attributes["data-card-id"]
    assert_equal unsafe, title.text
    assert_equal unsafe, detail.text
    assert_equal unsafe, path.attributes["id"]
    assert_equal unsafe, path.attributes["d"]
    assert_equal unsafe, path.attributes["data-route"]
    assert_equal unsafe, path.attributes["data-start-card"]
    assert_equal unsafe, path.attributes["data-end-card"]
  end

  private

  def minimal_svg
    <<~SVG
      <?xml version="1.0" encoding="UTF-8"?>
      <svg xmlns="http://www.w3.org/2000/svg" width="1600" height="1040" viewBox="0 0 1600 1040">
        <rect width="1600" height="1040" fill="#0b1322"/>
      </svg>
    SVG
  end
end

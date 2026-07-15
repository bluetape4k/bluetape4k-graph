#!/usr/bin/env ruby
# frozen_string_literal: true

require "open3"
require "rexml/document"
require "tempfile"

module ManualDiagramValidation
  ROOT = File.expand_path("../..", __dir__)
  WIDTH = 1600
  HEIGHT = 1040
  PNG_SIGNATURE = "\x89PNG\r\n\x1a\n".b
  MARKER_COLORS = {
    "arrow-cyan" => "#8bd5ff",
    "arrow-teal" => "#4fe0cf",
    "arrow-purple" => "#b8a1ff",
    "arrow-amber" => "#ffc857",
    "arrow-rose" => "#ff9aae",
  }.freeze
  ASSETS = {
    "repository-learning-map" => "docs/manual/assets/overview/repository-learning-map",
    "core-abstraction-map" => "docs/manual/assets/architecture/core-abstraction-map",
    "backend-decision-map" => "docs/manual/assets/backends/backend-decision-map",
    "graph-io-pipeline" => "docs/manual/assets/graph-io/graph-io-pipeline",
    "framework-integration-flow" => "docs/manual/assets/frameworks/framework-integration-flow",
  }.freeze

  class Validator
    attr_reader :summaries

    def initialize(root: ROOT, assets: ASSETS, cairo_binary: "cairosvg", command_runner: Open3.method(:capture3))
      @root = root
      @assets = assets
      @cairo_binary = cairo_binary
      @command_runner = command_runner
      @summaries = []
    end

    def errors
      validate
    end

    def validate(verbose: false)
      failures = []
      @summaries = []
      @assets.each { |name, relative| validate_asset(name, relative, failures) }
      @summaries.each { |summary| puts summary } if verbose
      failures
    end

    private

    def validate_asset(name, relative, failures)
      svg_path = File.join(@root, "#{relative}.svg")
      png_path = File.join(@root, "#{relative}.png")
      unless File.file?(svg_path) && File.file?(png_path)
        failures << "#{name}: missing SVG/PNG pair"
        return
      end

      source = File.binread(svg_path)
      document = REXML::Document.new(source)
      root = document.root
      failures << "#{name}: SVG dimensions must be 1600x1040" unless svg_dimensions_valid?(root)
      failures << "#{name}: missing accessible title/description" unless REXML::XPath.first(root, "title") && REXML::XPath.first(root, "desc")
      failures << "#{name}: dark navy theme missing" unless source.include?("#0b1322") && source.include?("#172238")
      failures << "#{name}: required fonts missing" unless source.include?("Architects Daughter") && source.include?("Comic Mono")

      cards = card_bounds(root)
      connectors = REXML::XPath.match(root, ".//path[contains(concat(' ', @class, ' '), ' connector ')]")
      marker_by_id = REXML::XPath.match(root, ".//marker").each_with_object({}) do |marker, index|
        index[marker.attributes["id"]] = marker
      end
      used_markers = {}
      q_bends = 0
      failures << "#{name}: cards=0" if cards.empty?
      failures << "#{name}: connectors=0" if connectors.empty?

      connector_segments = []
      connectors.each do |path|
        id = path.attributes["id"].to_s
        start_card = path.attributes["data-start-card"].to_s
        end_card = path.attributes["data-end-card"].to_s
        d = path.attributes["d"].to_s
        derived_route, q_count = route_from_path(d)
        declared_route = points(path.attributes["data-route"].to_s)
        q_bends += q_count

        failures << "#{name}: #{id} missing endpoint card" unless cards[start_card] && cards[end_card]
        failures << "#{name}: #{id} stroke width below 4" if path.attributes["stroke-width"].to_f < 4
        failures << "#{name}: #{id} uses forbidden L command" if d.match?(/\bL\b/i)
        failures << "#{name}: #{id} missing route geometry" if derived_route.size < 2
        failures << "#{name}: #{id} data-route does not match path d" unless same_route?(declared_route, derived_route)

        marker_match = path.attributes["marker-end"].to_s.match(/\Aurl\(#([^\)]+)\)\z/)
        marker_id = marker_match && marker_match[1]
        marker = marker_id && marker_by_id[marker_id]
        if marker.nil?
          failures << "#{name}: #{id} missing explicit marker definition"
        else
          used_markers[marker_id] = marker
          validate_used_marker(name, id, path, marker, failures)
        end

        next if derived_route.size < 2 || !cards[start_card] || !cards[end_card]

        failures << "#{name}: #{id} start endpoint detached/corner-adjacent" unless on_boundary?(derived_route.first, cards.fetch(start_card))
        failures << "#{name}: #{id} end endpoint detached/corner-adjacent" unless on_boundary?(derived_route.last, cards.fetch(end_card))
        derived_route.each_cons(2) do |a, b|
          failures << "#{name}: #{id} has diagonal route segment #{a.inspect}->#{b.inspect}" unless axis_aligned?(a, b)
        end
        turns = [derived_route.size - 2, 0].max
        failures << "#{name}: #{id} mixed/sharp corners q=#{q_count} turns=#{turns}" if turns.positive? && q_count < turns
        segments(derived_route).each do |segment|
          cards.each do |card_id, bounds|
            next if [start_card, end_card].include?(card_id)
            failures << "#{name}: #{id} intrudes card #{card_id}" if segment_hits_interior?(segment, bounds)
          end
          connector_segments << [id, segment]
        end
      rescue ArgumentError => error
        failures << "#{name}: #{id} invalid path geometry: #{error.message}"
      end

      failures << "#{name}: used_markers=0" if used_markers.empty?
      connector_segments.combination(2) do |(left_id, left), (right_id, right)|
        next if left_id == right_id
        failures << "#{name}: connectors #{left_id}/#{right_id} cross" if crossing?(left, right)
      end

      dimensions = png_dimensions(png_path)
      failures << "#{name}: PNG dimensions #{dimensions || 'unreadable'}, expected 3200x2080" unless dimensions == [WIDTH * 2, HEIGHT * 2]
      validate_raster_parity(name, svg_path, png_path, failures)
      @summaries << "#{name}: cards=#{cards.size} connectors=#{connectors.size} q_bends=#{q_bends} used_markers=#{used_markers.size} dimensions=#{dimensions&.join('x') || 'unreadable'}"
    rescue REXML::ParseException => error
      failures << "#{name}: invalid SVG XML: #{error.message}"
    end

    def svg_dimensions_valid?(root)
      root && root.attributes["width"] == WIDTH.to_s && root.attributes["height"] == HEIGHT.to_s && root.attributes["viewBox"] == "0 0 #{WIDTH} #{HEIGHT}"
    end

    def card_bounds(root)
      REXML::XPath.match(root, ".//rect[contains(concat(' ', @class, ' '), ' card ')]").each_with_object({}) do |rect, cards|
        x = rect.attributes["x"].to_f
        y = rect.attributes["y"].to_f
        cards[rect.attributes["data-card-id"]] = [x, y, x + rect.attributes["width"].to_f, y + rect.attributes["height"].to_f]
      end
    end

    def validate_used_marker(name, connector_id, path, marker, failures)
      marker_id = marker.attributes["id"]
      fixed = marker.attributes["markerUnits"] == "userSpaceOnUse" && marker.attributes["markerWidth"] == "14" && marker.attributes["markerHeight"] == "14"
      failures << "#{name}: marker #{marker_id} used by #{connector_id} is not fixed 14x14 userSpaceOnUse" unless fixed
      polygon = REXML::XPath.first(marker, "polygon")
      expected_color = MARKER_COLORS[marker_id]
      failures << "#{name}: marker #{marker_id} used by #{connector_id} is outside the semantic palette" unless expected_color
      matches = expected_color && polygon && polygon.attributes["fill"] == expected_color && path.attributes["stroke"] == expected_color
      failures << "#{name}: marker #{marker_id} color differs from #{connector_id}" unless matches
    end

    def points(route)
      route.split.map do |pair|
        values = pair.split(",")
        raise ArgumentError, "invalid data-route" unless values.size == 2
        values.map { |value| Float(value) }
      end
    end

    def route_from_path(d)
      tokens = d.scan(/[MHVQ]|-?(?:\d+(?:\.\d*)?|\.\d+)/)
      residue = d.gsub(/[MHVQ]|-?(?:\d+(?:\.\d*)?|\.\d+)|[\s,]+/, "")
      raise ArgumentError, "unsupported command #{residue}" unless residue.empty?

      operations = []
      current = nil
      index = 0
      while index < tokens.size
        command = tokens[index]
        index += 1
        case command
        when "M"
          current = [number(tokens, index), number(tokens, index + 1)]
          index += 2
          operations << [:move, current]
        when "H"
          raise ArgumentError, "H before M" unless current
          current = [number(tokens, index), current[1]]
          index += 1
          operations << [:line, current]
        when "V"
          raise ArgumentError, "V before M" unless current
          current = [current[0], number(tokens, index)]
          index += 1
          operations << [:line, current]
        when "Q"
          raise ArgumentError, "Q before M" unless current
          control = [number(tokens, index), number(tokens, index + 1)]
          current = [number(tokens, index + 2), number(tokens, index + 3)]
          index += 4
          operations << [:curve, control, current]
        else
          raise ArgumentError, "expected path command, got #{command.inspect}"
        end
      end
      raise ArgumentError, "path must start with M" unless operations.first&.first == :move

      route = [operations.first[1]]
      operations.drop(1).each_with_index do |operation, offset|
        next_operation = operations[offset + 2]
        case operation.first
        when :line
          route << operation[1] unless next_operation&.first == :curve
        when :curve
          route << operation[1]
          route << operation[2] if next_operation.nil? || next_operation.first == :curve
        end
      end
      [route.each_with_object([]) { |point, unique| unique << point unless unique.last == point }, operations.count { |operation| operation.first == :curve }]
    end

    def number(tokens, index)
      token = tokens[index]
      raise ArgumentError, "missing coordinate" unless token && token.match?(/\A-?(?:\d+(?:\.\d*)?|\.\d+)\z/)
      Float(token)
    end

    def same_route?(left, right)
      left.size == right.size && left.zip(right).all? do |left_point, right_point|
        left_point.zip(right_point).all? { |a, b| (a - b).abs < 0.001 }
      end
    end

    def on_boundary?(point, card)
      x, y = point
      left, top, right, bottom = card
      horizontal = (x - left).abs < 0.1 || (x - right).abs < 0.1
      vertical = (y - top).abs < 0.1 || (y - bottom).abs < 0.1
      within_x = x >= left + 14 && x <= right - 14
      within_y = y >= top + 14 && y <= bottom - 14
      (horizontal && within_y) || (vertical && within_x)
    end

    def axis_aligned?(a, b)
      (a[0] - b[0]).abs < 0.001 || (a[1] - b[1]).abs < 0.001
    end

    def segments(route_points)
      route_points.each_cons(2).to_a
    end

    def segment_hits_interior?(segment, card)
      a, b = segment
      left, top, right, bottom = card
      if (a[0] - b[0]).abs < 0.001
        x = a[0]
        low, high = [a[1], b[1]].minmax
        x > left && x < right && high > top && low < bottom
      else
        y = a[1]
        low, high = [a[0], b[0]].minmax
        y > top && y < bottom && high > left && low < right
      end
    end

    def crossing?(first, second)
      a, b = first
      c, d = second
      return false if [a, b].any? { |point| point == c || point == d }
      if (a[0] - b[0]).abs < 0.001 && (c[1] - d[1]).abs < 0.001
        x, y = a[0], c[1]
        y.between?(*[a[1], b[1]].minmax) && x.between?(*[c[0], d[0]].minmax)
      elsif (a[1] - b[1]).abs < 0.001 && (c[0] - d[0]).abs < 0.001
        x, y = c[0], a[1]
        y.between?(*[c[1], d[1]].minmax) && x.between?(*[a[0], b[0]].minmax)
      else
        false
      end
    end

    def png_dimensions(path)
      data = File.binread(path, 24)
      return unless data.start_with?(PNG_SIGNATURE) && data.bytesize >= 24
      data.byteslice(16, 8).unpack("NN")
    rescue Errno::ENOENT
      nil
    end

    def validate_raster_parity(name, svg_path, png_path, failures)
      Tempfile.create(["diagram-parity-", ".png"], File.dirname(png_path)) do |rendered|
        rendered.close
        stdout, stderr, status = @command_runner.call(@cairo_binary, svg_path, "-o", rendered.path, "-s", "2")
        unless status.success?
          failures << "#{name}: CairoSVG parity render failed: #{stdout}#{stderr}"
          return
        end
        failures << "#{name}: PNG does not match SVG render at scale 2" unless File.binread(rendered.path) == File.binread(png_path)
      end
    end
  end
end

if $PROGRAM_NAME == __FILE__
  validator = ManualDiagramValidation::Validator.new
  failures = validator.validate(verbose: true)
  if failures.empty?
    puts "diagram validation: failures=0"
  else
    warn failures.join("\n")
    exit 1
  end
end

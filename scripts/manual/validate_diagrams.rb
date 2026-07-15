#!/usr/bin/env ruby
# frozen_string_literal: true

require "rexml/document"
require "open3"

ROOT = File.expand_path("../..", __dir__)
ASSETS = {
  "repository-learning-map" => "docs/manual/assets/overview/repository-learning-map",
  "core-abstraction-map" => "docs/manual/assets/architecture/core-abstraction-map",
  "backend-decision-map" => "docs/manual/assets/backends/backend-decision-map",
  "graph-io-pipeline" => "docs/manual/assets/graph-io/graph-io-pipeline",
  "framework-integration-flow" => "docs/manual/assets/frameworks/framework-integration-flow",
}.freeze

def points(route)
  route.split.map { |pair| pair.split(",").map(&:to_f) }
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

def segments(route_points)
  route_points.each_cons(2).to_a
end

def segment_hits_interior?(segment, card)
  (a, b) = segment
  left, top, right, bottom = card
  if a[0] == b[0]
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
  (a, b) = first
  (c, d) = second
  return false if [a, b].any? { |point| point == c || point == d }
  if a[0] == b[0] && c[1] == d[1]
    x, y = a[0], c[1]
    y.between?(*[a[1], b[1]].minmax) && x.between?(*[c[0], d[0]].minmax)
  elsif a[1] == b[1] && c[0] == d[0]
    x, y = c[0], a[1]
    y.between?(*[c[1], d[1]].minmax) && x.between?(*[a[0], b[0]].minmax)
  else
    false
  end
end

failures = []
ASSETS.each do |name, relative|
  svg_path = File.join(ROOT, "#{relative}.svg")
  png_path = File.join(ROOT, "#{relative}.png")
  unless File.file?(svg_path) && File.file?(png_path)
    failures << "#{name}: missing SVG/PNG pair"
    next
  end

  source = File.read(svg_path)
  document = REXML::Document.new(source)
  root = document.root
  failures << "#{name}: SVG dimensions must be 1600x1040" unless root.attributes["width"] == "1600" && root.attributes["height"] == "1040" && root.attributes["viewBox"] == "0 0 1600 1040"
  failures << "#{name}: missing accessible title/description" unless REXML::XPath.first(root, "title") && REXML::XPath.first(root, "desc")
  failures << "#{name}: dark navy theme missing" unless source.include?("#0b1322") && source.include?("#172238")
  failures << "#{name}: required fonts missing" unless source.include?("Architects Daughter") && source.include?("Comic Mono")
  %w[#8bd5ff #4fe0cf #b8a1ff #ffc857 #ff9aae].each do |color|
    failures << "#{name}: semantic color #{color} missing" unless source.include?(color)
  end

  cards = {}
  REXML::XPath.each(root, ".//rect[contains(concat(' ', @class, ' '), ' card ')]") do |rect|
    cards[rect.attributes["data-card-id"]] = [rect.attributes["x"].to_f, rect.attributes["y"].to_f, rect.attributes["x"].to_f + rect.attributes["width"].to_f, rect.attributes["y"].to_f + rect.attributes["height"].to_f]
  end
  connectors = REXML::XPath.match(root, ".//path[contains(concat(' ', @class, ' '), ' connector ')]")
  markers = REXML::XPath.match(root, ".//marker")
  q_bends = connectors.sum { |path| path.attributes["d"].scan(/\bQ\b/).size }
  failures << "#{name}: cards=0" if cards.empty?
  failures << "#{name}: connectors=0" if connectors.empty?
  failures << "#{name}: q_bends=0" if q_bends.zero?
  failures << "#{name}: expected five explicit color markers" unless markers.size == 5
  markers.each do |marker|
    unless marker.attributes["markerUnits"] == "userSpaceOnUse" && marker.attributes["markerWidth"] == "14" && marker.attributes["markerHeight"] == "14"
      failures << "#{name}: marker #{marker.attributes['id']} is not fixed 14x14 userSpaceOnUse"
    end
  end

  connector_segments = []
  connectors.each do |path|
    id = path.attributes["id"]
    route = points(path.attributes["data-route"].to_s)
    start_card = path.attributes["data-start-card"]
    end_card = path.attributes["data-end-card"]
    failures << "#{name}: #{id} missing endpoint card" unless cards[start_card] && cards[end_card]
    failures << "#{name}: #{id} stroke width below 4" if path.attributes["stroke-width"].to_f < 4
    failures << "#{name}: #{id} missing explicit marker" unless path.attributes["marker-end"].to_s.match?(/url\(#arrow-(cyan|teal|purple|amber|rose)\)/)
    failures << "#{name}: #{id} uses forbidden L command" if path.attributes["d"].match?(/\bL\b/)
    if route.size < 2
      failures << "#{name}: #{id} missing route geometry"
      next
    end
    failures << "#{name}: #{id} start endpoint detached/corner-adjacent" unless on_boundary?(route.first, cards.fetch(start_card))
    failures << "#{name}: #{id} end endpoint detached/corner-adjacent" unless on_boundary?(route.last, cards.fetch(end_card))
    route.each_cons(2) do |a, b|
      failures << "#{name}: #{id} has diagonal route segment #{a.inspect}->#{b.inspect}" unless a[0] == b[0] || a[1] == b[1]
    end
    turns = [route.size - 2, 0].max
    q_count = path.attributes["d"].scan(/\bQ\b/).size
    failures << "#{name}: #{id} mixed/sharp corners q=#{q_count} turns=#{turns}" if q_count < turns
    segments(route).each do |segment|
      cards.each do |card_id, bounds|
        next if [start_card, end_card].include?(card_id)
        failures << "#{name}: #{id} intrudes card #{card_id}" if segment_hits_interior?(segment, bounds)
      end
      connector_segments << [id, segment]
    end
  end

  connector_segments.combination(2) do |(left_id, left), (right_id, right)|
    next if left_id == right_id
    failures << "#{name}: connectors #{left_id}/#{right_id} cross" if crossing?(left, right)
  end

  dimensions, identify_error, identify_status = Open3.capture3("identify", "-format", "%wx%h", png_path)
  failures << "#{name}: PNG identify failed: #{identify_error.strip}" unless identify_status.success?
  failures << "#{name}: PNG dimensions #{dimensions}, expected 3200x2080" unless dimensions == "3200x2080"
  puts "#{name}: cards=#{cards.size} connectors=#{connectors.size} q_bends=#{q_bends} markers=#{markers.size} dimensions=#{dimensions}"
end

if failures.empty?
  puts "diagram validation: failures=0"
else
  warn failures.join("\n")
  exit 1
end

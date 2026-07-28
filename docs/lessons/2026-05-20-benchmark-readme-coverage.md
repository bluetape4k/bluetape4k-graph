# Benchmark README Coverage

## 맥락

The README diagram quality pass found benchmark modules without module-level
README files, which made the generated architecture diagrams incomplete for
the graph benchmark surface.

## 결정

Add source-verified English and Korean README pairs for the benchmark modules
and place a compact architecture diagram near the top of each README.

## 결과

The AGE, core graph, graph I/O, and Neo4j benchmark modules now explain what
they measure, how to run them, and which source classes back the documented
behavior.

## 검증

- Rendered new SVG diagrams to PNG with `rsvg-convert`.
- Ran the README diagram quality audit and confirmed zero critical findings for
  `module-missing-readme`, text overlap, canvas clipping, sequence label
  clipping, and zero-length arrows.

## 향후 지침

When adding benchmark modules, add README.md and README.ko.md with a small
source-verified architecture diagram instead of relying only on root docs.

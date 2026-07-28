# Graph 0.5.0 Release 준비

## 맥락

`bluetape4k-dependencies` 1.2.0 needs the latest stable graph line. The graph
repository already has `baseVersion=0.5.0`, consumes
`io.github.bluetape4k:bluetape4k-bom:1.10.0`, and includes the completed 0.5.0
Ktor managed backend and domain example milestone work.

## 결정

Prepare `0.5.0` as the next stable graph release. Keep README unchanged because
the English and Korean root README files already list the new example modules
and example test commands.

## 결과

Release metadata now has a dated `0.5.0` changelog section, refreshed WIP state,
and explicit pre-release evidence. The milestone has no open issues and no open
pull requests.

## 검증

- GitHub milestone `0.5.0`: open issues `0`, closed issues `35`.
- GitHub open PRs: none.
- CI succeeded on commit `8e4abdd`:
  `https://github.com/bluetape4k/bluetape4k-graph/actions/runs/26733305940`.
- Examples succeeded on commit `8e4abdd`:
  `https://github.com/bluetape4k/bluetape4k-graph/actions/runs/26733305942`.
- Maven Central correctly returns 404 for
  `io.github.bluetape4k.graph:bluetape4k-graph-bom:0.5.0` before release.

## 향후 지침

Before publishing a graph stable release, make the target changelog section
dated and refresh WIP from live GitHub issue and PR state. README edits are not
required when the current module list and usage snippets already describe the
release content.

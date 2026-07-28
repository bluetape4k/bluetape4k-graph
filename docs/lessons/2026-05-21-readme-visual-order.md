# README 시각 자료 순서

## 맥락

The Korean root README placed architecture and database detail before the
overview visuals. It also linked the class diagram label to the architecture
image, duplicating the same image twice.

## 결정

Keep Overview Diagram and Module Composition Chart before architecture detail in
localized README files, and keep image labels aligned with the linked asset.

## 결과

`README.ko.md` now presents root visuals first, then architecture and supported
database details. The class diagram link points to the class asset, and generated
overview labels use lowercase module names.

## 검증

- `git diff --check`
- `xmllint --noout` for changed SVG assets
- `rsvg-convert` PNG rendering
- README image-link existence scan

## 다음 단계

Future generated overview assets should preserve exact Gradle module casing and
README section order across locales.

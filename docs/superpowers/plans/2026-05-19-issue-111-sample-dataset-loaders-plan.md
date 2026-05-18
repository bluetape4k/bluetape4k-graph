# Issue #111 Sample Dataset Loaders Plan

## Steps

1. Add graph-io CSV dependencies to the three domain example modules.
2. Add one public sample dataset loader per module with sync and suspend import entry points.
3. Add bundled CSV fixtures for fraud, recommendation, and knowledge graph datasets.
4. Add TinkerGraph smoke tests that verify import reports and domain service queries.
5. Update English and Korean READMEs with import flow and verification scope.
6. Run targeted compile, tests, Detekt, Codex review, and Claude review before PR.

## Done When

- The three sample loaders import their default CSV fixtures successfully.
- Tests prove the imported graphs work with each domain service.
- Public docs and KDoc use current API names.
- PR review artifacts include both Codex and Claude verdicts.

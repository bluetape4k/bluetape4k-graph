# AGENTS.md - graph-ktor

This module inherits `../../../AGENTS.md` (workspace root) and
`../../AGENTS.md` (bluetape4k-graph repo guide). Read both first. This file
only narrows graph-ktor-specific rules.


Ktor integration module for bluetape4k-graph.

- Keep the core plugin backend-neutral: depend on `graph-core` plus Ktor only.
- Backend helper files may use `compileOnly` backend modules, but must not turn those backends into runtime dependencies.
- Public APIs need English KDoc with behavior/contract notes and realistic usage examples.
- Route-level helpers should use `ApplicationCall` extensions; application setup helpers should use `Application` extensions.
- Caller-owned drivers, data sources, and Exposed `Database` lifecycle must not be closed by this module.
- Tests should use Ktor `testApplication`; prefer TinkerGraph for in-memory smoke coverage.

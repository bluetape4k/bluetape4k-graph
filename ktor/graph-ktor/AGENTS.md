# AGENTS.md - graph-ktor

Ktor integration module for bluetape4k-graph.

- Keep the core plugin backend-neutral: depend on `graph-core` plus Ktor only.
- Backend helper files may use `compileOnly` backend modules, but must not turn those backends into runtime dependencies.
- Public APIs need Korean KDoc with behavior/contract notes and realistic usage examples.
- Route-level helpers should use `ApplicationCall` extensions; application setup helpers should use `Application` extensions.
- Caller-owned drivers, data sources, and Exposed `Database` lifecycle must not be closed by this module.
- Tests should use Ktor `testApplication`; prefer TinkerGraph for in-memory smoke coverage.

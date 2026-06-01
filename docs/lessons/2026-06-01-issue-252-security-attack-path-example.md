# Issue 252 Security Attack-Path Example

## Context

Issue #252 asked for a bounded educational security attack-path graph example for the 0.5.0 milestone.

## Decision

Add `security-attack-path-examples` as a graph-io CSV-backed TinkerGraph example with sync and coroutine services.
The model covers entry assets, hosts, principals, credentials, vulnerabilities, permissions, and crown-jewel hosts.
It explicitly documents that the module is not a scanner.

## Outcome

The first slice demonstrates shortest attack paths, risk-ranked path enumeration, credential-based privilege escalation,
unreachable crown-jewel detection, and remediation impact by cutting one edge.

## Verification

Run the module tests and Examples workflow before merging. The expected local commands are:

```bash
./gradlew :security-attack-path-examples:test --no-daemon
./gradlew :security-attack-path-examples:build --no-daemon
./gradlew projects --no-daemon
git diff --check
```

## Future Notes

Keep future security-domain examples educational and deterministic. Do not add vulnerability feeds, scanners, or external
security dependencies without a separate design issue.

# Graph 0.4.0 Release Prep

## Context

Graph `develop` contains both the closed `0.3.1` milestone work and the closed
`0.4.0` benchmark/self-improve work. Publishing `0.3.1` from the current head
would duplicate the same code under both patch and minor versions.

## Decision

Prepare `bluetape4k-graph` as the next minor release, `0.4.0`, and align the
release with the published bluetape4k 1.9.0 BOM.

## Outcome

Release metadata, dependency catalog, CHANGELOG, WIP, and release-prep lesson
were updated for the 0.4.0 release gate.

## Verification

Verified the Gradle release version, workflow syntax, publication POM generation,
stale/snapshot POM absence, build, and local Maven publication before opening
the release PR.

## Future Notes

Do not publish a separate 0.3.1 release from the same commit range. Start Neptune
research (#113) only after 0.4.0 is released.

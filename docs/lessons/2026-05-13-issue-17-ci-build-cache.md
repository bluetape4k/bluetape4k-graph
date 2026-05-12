# Issue #17 CI Build Cache

- Context: Issue #17 already had Gradle caching enabled, but Testcontainers jobs still pulled graph database images without a persistent image archive cache.
- Decision: Cache Docker image archives for the fixed graph Testcontainers images and load them before container-backed jobs.
- Outcome: CI and Nightly Testcontainers jobs restore `/tmp/testcontainers-image-cache`, load any saved images, run tests, then save pulled images so the actions/cache post-step can persist them.
- Verification: `actionlint .github/workflows/ci.yml .github/workflows/nightly.yml` and `bash -n .github/scripts/docker-image-cache.sh`.
- Future rule: For Testcontainers image-cache work, cache the exact pinned launcher images, not a broad Docker directory, and include the image list in the cache key.

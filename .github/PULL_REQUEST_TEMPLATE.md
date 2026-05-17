## Description

<!-- Meaningful description here -->

## APK testing

For each change in the pull request, a workflow is run, which produces a debug artifact APK. Go to Checks -> FlorisBoard CI -> `app-debug.apk` and download the APK. It installs under the `dev.patrickgold.florisboard.debug` namespace and will not mess with your main installation.

## Checklist

- [ ] I have read and understood the [SwiftFloris contribution guidelines](../CONTRIBUTING.md).
- [ ] I have checked the base-app invariants in [PROJECT_CONTEXT.md](../PROJECT_CONTEXT.md): no network permission, no telemetry/account binding, Apache-2.0-compatible `:app`, and no closed blobs.
- [ ] I have listed the exact Gradle/manual verification I ran, or explained why maintainer-host verification is still required.

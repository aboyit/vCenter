# Changelog

## 2026-11-21

### Git Publication Without Test Sources

- Remove all unit-test and instrumentation-test source files from disk and Git at the user's explicit request, including newly added placement/dialog tests.
- Publish current application, documentation, and Android development workflow changes; retain local SDK/JDK, caches, build outputs, and private configuration outside Git.
- README marks previous test instructions and results as historical after test-source removal.

### Compact VM Configuration And HTTP Errors

- Compact the VM editor: place vCPU and cores per socket side by side, wrap hot-add controls, reduce spacing, and share a footer row for reload/close.
- Remove the redundant infrastructure/timeout warning from advanced confirmation while preserving explicit confirmation and no automatic mutation retries.
- Report the actual SOAP API on HTTP 401/403 instead of incorrectly labeling every post-login request as logout. Distinguish HTTP errors after dispatch from lost network responses, retaining uncertain-command reconciliation because an HTTP error alone cannot prove non-execution.
- HTTP 403 from NPM/WAF/upstream still requires server-side diagnosis; no infrastructure mutation or proxy bypass is performed.
- Verified 11 targeted unit tests and all 7 advanced-dialog instrumentation tests on SM-N976N; built and installed the debug update with existing app data retained, and confirmed successful launch.

### Host-First Clone And Migration

- Require destination ESXi selection before showing dependent clone/migration fields; scope resource pools to its compute/cluster, VM folders to its datacenter, and accessible datastores to its mounted storage.
- Key dependent selection state by ESXi so changing hosts clears old folder, resource pool, and datastore choices. Preserve migration storage only when the destination can access all current VM datastores.
- Add placement unit tests and clone/migration UI regression tests for destination filtering, host switching, and confirmation; update the existing detail-dialog test for ESXi-first selection.
- Verified placement, SOAP, and inventory unit tests; debug/UI-test APK builds; all 7 VmAdvancedPanelTest cases passed on the connected SM-N976N using fixture data, without vCenter mutations.

### VM Management Dialogs

- Configuration, clone, and migration now open dedicated dialogs instead of appending a shared debug form inside VM details.
- Removed milestone and changeVersion text; added readable VM power status, aligned full-width selection fields, and a separate close action.
- Configuration keeps hardware groups; clone and migration no longer expose unrelated groups. Migration initially preserves the current datastore.
- Validation appears after Continue, not on an untouched form. Review summaries use names and readable changes instead of Kotlin data-class output.
- Retained server validation and explicit confirmation before submission.
- Debug APK build and all five advanced-dialog UI tests passed on the unlocked Samsung SM-N976N. Earlier UI runs were blocked by the phone keyguard, not application assertions.

- Removed manual operation reconciliation and historical pending-command locks for power, snapshots, configuration, clone, and migration.
- Follow returned vCenter Task references immediately after dispatch and poll every 3 seconds while foreground operations remain unresolved.
- Distinguish explicit SOAP rejection from an unknown outcome after response loss. Keep unknown outcomes honest without automatically resending commands.
- Observe guest shutdown through fresh server power state; record reboot acceptance without claiming that the guest reboot completed or creating a fake Task.
- Retain confirmation, fresh-state validation, durable command history, and protection against simultaneous request submission.
- Added focused policy, SOAP response, and UI regressions. Focused unit tests and the debug APK build passed; all 17 affected UI tests passed on Samsung SM-N976N after waking the test device and rerunning (the first run had six Compose hierarchy startup failures).
- Live infrastructure operations are not executed during validation.

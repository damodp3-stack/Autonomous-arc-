# Project State

This document represents the audited, actual state of the Autonomous Arc Android repository.

## Current Stage
Phase 9 — Dynamic AI Model Discovery as Authoritative Source of Truth, Capability-Driven Safe Fallback, Granular Diagnostic Classification, and Media & Cloud Resilience.

## Completed
- **Dynamic Model Discovery as Source of Truth** (`DiscoveredModel`, `ModelDiscoveryProvider`, `GeminiModelDiscoveryProvider`, `OpenAIModelDiscoveryProvider`, `AnthropicModelDiscoveryProvider`, `MockModelDiscoveryProvider`) [IMPLEMENTED]: Live discovery queries the provider API (`v1beta/models`, `v1/models`) to determine actual live model availability. Prohibited and obsolete models (e.g., `gemini-2.0-flash`, `gemini-1.5-pro`, `gemini-1.5-flash`, `gemini-pro`) are strictly excluded.
- **Static Catalog Safety** (`AIModelCatalog`, `AIModelRegistry`) [IMPLEMENTED]: Separated static catalog into offline metadata, descriptions, and reference documentation. Catalog items are marked non-live (`isAvailable = false`, `isVerifiedLive = false`) and never override live discovery.
- **Model Selection & 4-State UI** (`ModelSelectionRepository`, `RealModelSelectionRepository`, `AIProviderSettingsDialog`) [IMPLEMENTED]: Cleanly enforces four operational states:
  1. *No API key configured*: Displays "Configuration required — No verified live models" and provides offline catalog only as informational metadata.
  2. *API key configured + discovery succeeds*: Displays only live-discovered verified models matching capabilities.
  3. *API key configured + discovery fails + previous discovery exists*: Displays cached models with `CACHED` badge, timestamp of last successful refresh, and current error details.
  4. *API key configured + discovery fails + no previous discovery*: Displays "Unable to verify models" with retry option and provider error details, never populating the UI with static catalog models as available.
- **Capability-Driven Safe Fallback** (`ModelSelectionRepository.getCompatibleFallback`) [IMPLEMENTED]: Filters candidate models strictly by capability (text/code vs image vs video). Never invents a model ID; returns null if no compatible discovered candidate exists.
- **Consistent Model ID Normalization** (`ModelIdNormalizer`) [IMPLEMENTED]: Strips single or duplicated prefixes (e.g., `models/models/gemini-x` -> `gemini-x`), trims whitespace, and applies uniform IDs across discovery, persistence, testing, and runtime requests.
- **Granular Connection Diagnostics** (`AIFactory.testConnectionDetailed`, `classifyHttpError`) [IMPLEMENTED]: Real connection tests against actual selected provider and model without silent substitutions. Classifies errors into `MODEL_NOT_FOUND` (404), `AUTHENTICATION_FAILED` (401), `FORBIDDEN` (403), `QUOTA_EXCEEDED` (429), `RATE_LIMITED` (429), `SERVER_ERROR` (5xx), and `NETWORK_ERROR`.
- **Workspace Runtime Fallback** (`WorkspaceViewModel.sendMessage`, `WorkspaceViewModel.proposeChange`) [IMPLEMENTED]: On 404/model rejection, triggers dynamic discovery and switches to a compatible discovered model, persisting the new selection and notifying the user.
- **Media Vault & Generation** (`GeminiMediaGenerationProvider`, `MockMediaGenerationProvider`, `MediaVaultViewModel`) [IMPLEMENTED]: Real bytes decoded and persisted to local file storage before `MediaEntity` insertion. Provider failures and billing/quota limits (HTTP 429) are preserved as failures without creating fake records.
- **Cloud Sync Foundation** (`LocalOnlySyncProvider`, `MockCloudSyncProvider`, `RestCloudSyncProvider`, `RealSyncRepository`) [IMPLEMENTED]: Local Room database is the authoritative single source of truth. `LocalOnlySyncProvider` is active by default.

## Verified
- **Unit & Robolectric Test Suite** (`gradle :app:testDebugUnitTest`) [VERIFIED]: 157 passing tests, 0 failures, 0 errors, 0 skipped.
- **Model Truth & Cache Isolation** (`Phase9HardeningTest`) [VERIFIED]: Verified that static catalog is never treated as live; successful discovery is authoritative; cached discovery handles network drops; failed initial discovery displays unverified state; no-key state displays config required.
- **Model ID Normalization** [VERIFIED]: Regression tested for single/multi `models/` prefix stripping, whitespace trimming, and duplicate prefix prevention.
- **Strict Capability Fallback** [VERIFIED]: Verified that image requests never fall back to text models, text requests never fall back to image models, and missing candidates return null.
- **Diagnostic Error Categorization** [VERIFIED]: Verified classification of HTTP 401, 403, 404, 429 quota, 429 rate limit, 500, 503, and network errors.
- **Security & Privacy** [VERIFIED]: Audited repo; no exposed API keys, PATs, or tokens found; error handlers and analytics do not leak secrets.
- **Debug APK Build** (`gradle :app:assembleDebug`) [VERIFIED]: Generated `app/build/outputs/apk/debug/app-debug.apk` (24 MB).
- **Release APK Build** (`gradle :app:assembleRelease`) [VERIFIED]: Generated `app/build/outputs/apk/release/app-release.apk` (16 MB).
- **Release Bundle Build** (`gradle :app:bundleRelease`) [VERIFIED]: Generated `app/build/outputs/bundle/release/app-release.aab` (15.8 MB).

## Partially Verified
- **REST Cloud Sync Adapter** (`RestCloudSyncProvider`) [PARTIALLY VERIFIED]: REST adapter contract, JSON payload formatting, and HTTP conflict detection (HTTP 409) tested with mock/in-memory provider. Live remote sync requires user deployment of an external REST/Supabase backend.

## Pending
- **Production Cloud Backend Deployment** [PENDING]: Deployment of dedicated remote database endpoint or Firebase Firestore sync infrastructure for multi-device sync.

## Known Limitations
- **Gemini Media Generation Quota** [REQUIRES PROVIDER/BILLING]: Gemini free-tier API keys do not currently permit image generation (HTTP 429 quota limit 0). A Google Cloud project with active billing / paid tier quota is required for real AI image generation.
- **Cloud Sync Configuration**: Operates in `LocalOnlySyncProvider` offline mode by default until external endpoint URL and bearer token are supplied in settings.
- **GitHub Push**: Remote git push from the container environment requires interactive authentication or user GitHub Personal Access Token.

## Errors/Bugs
- None. Deprecated Gemini models (such as `gemini-2.0-flash`, `gemini-1.5-pro`) are blocked from discovery and automatically substituted with live models via runtime fallback if previously persisted.

## Latest Commit
- Commit: `13acad19515be0e5130bc3e430a996dc7079d300`
- Branch: `main`
- Status: Local git branch is clean and ahead of remote origin; remote push requires user token credentials.

## Tests
- `gradle :app:testDebugUnitTest`: 157 passed, 0 failures, 0 errors, 0 skipped.
  - `Phase9HardeningTest`: 20/20 passed
  - `AutonomousExecutionEngineTest`: 28/28 passed
  - `ProjectFileSystemTest`: 17/17 passed
  - `GitHubPushTest`: 16/16 passed
  - `GitHubCloneTest`: 14/14 passed
  - `CodeChangeApplierTest`: 13/13 passed
  - `IdeasVaultTest`: 5/5 passed
  - `MediaVaultTest`: 5/5 passed
  - `UsageAnalyticsTest`: 5/5 passed
  - `AIModelRegistryTest`: 5/5 passed
  - `CloudSyncTest`: 4/4 passed
  - `AIFactoryTest`: 4/4 passed
  - `CodeChangeProposalTest`: 4/4 passed
  - `FallbackAIProviderTest`: 4/4 passed
  - `ProductionHardeningTest`: 4/4 passed
  - `APIKeyManagerTest`: 3/3 passed
  - `GitHubIntegrationTest`: 2/2 passed
  - `ProjectFileRepositoryTest`: 2/2 passed
  - `ExampleRobolectricTest`: 1/1 passed
  - `ExampleUnitTest`: 1/1 passed

## Builds
- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk` (24 MB) — Verified
- **Release APK**: `app/build/outputs/apk/release/app-release.apk` (16 MB) — Verified
- **Release Bundle (AAB)**: `app/build/outputs/bundle/release/app-release.aab` (15.8 MB) — Verified

## Next Milestone
- Milestone 10: Production Cloud Sync Integration & Verified Billing Tier for Media Generation.

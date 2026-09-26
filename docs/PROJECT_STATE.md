# Project State

This document represents the current actual state of the repository.

**Current Stage**
Phase 9 — Live AI Model Discovery, Gemini 404 Resolution, Provider Hardening & Real Cloud/Media Foundation (Completed).

**Completed**
- **Live AI Model Discovery Architecture (`DiscoveredModel`, `ModelDiscoveryProvider`, `GeminiModelDiscoveryProvider`, `OpenAIModelDiscoveryProvider`, `AnthropicModelDiscoveryProvider`, `MockModelDiscoveryProvider`):**
  - Eliminated stale hardcoded model assumptions (`gemini-2.0-flash`, `gemini-1.5-pro`, `gemini-1.5-flash`).
  - Implemented live model discovery calling `GET v1beta/models`, authenticating via configured API key, and filtering out obsolete/deprecated models while verifying `generateContent` capability.
  - Exposes model ID, display name, description, generation methods, image generation capability flags, and token limits.
  - Multi-provider support for Gemini, OpenAI (`v1/models`), Anthropic (`v1/models`), and Mock providers.

- **Model Selection Repository & Caching (`ModelSelectionRepository`, `RealModelSelectionRepository`, `ModelDiscoveryState`):**
  - Manages reactive discovery state flows (`isLoading`, `models`, `errorMessage`, `lastRefreshedTimestamp`, `isFromCache`).
  - Thread-safe in-memory and fallback caching without storing sensitive credentials in model caches.
  - Graceful network degradation: preserves last known good discovered models on network error and alerts user via state.

- **Refactored Model Registry & Static Catalog (`AIModelCatalog`, `AIModelRegistry`):**
  - Separated static catalog fallback metadata (`AIModelCatalog`) from live provider availability.
  - `AIModelRegistry` preserved with 100% backward compatibility for all legacy tests while delegating to verified models.

- **Upgraded Connection Diagnostics (`AIFactory.testConnection`):**
  - Tests the ACTUAL selected provider and ACTUAL selected model without silent pre-substitution.
  - Differentiates specific error categories:
    - HTTP 401: Invalid API key.
    - HTTP 403: Forbidden / unauthorized project access.
    - HTTP 404: Selected model is unavailable, shut down, or unsupported on the provider API.
    - HTTP 429: Rate limit or quota exhausted.
    - HTTP 503: Service high demand / temporary overload.
    - HTTP 500-599: Internal provider server errors.
    - `UnknownHostException` / `SocketTimeoutException`: Network connection and timeout failures.

- **Automatic Model Fallback & Transparency (`WorkspaceViewModel.sendMessage`, `proposeChange`):**
  - Detects runtime 404 / unavailable model errors during generation and code proposal generation.
  - Selects compatible live model via `getCompatibleFallback` (preserving text/code vs image capabilities).
  - Automatically updates and persists new selection in `AIProviderConfigRepository`.
  - Informs the user explicitly via chat timeline message about the fallback switch.

- **Real Media Generation Foundation (`MediaGenerationProvider`, `GeminiMediaGenerationProvider`, `MockMediaGenerationProvider`, `MediaCapability`):**
  - Clean provider abstraction exposing capabilities (`IMAGE`, `VIDEO`, `TEXT_TO_IMAGE`, `IMAGE_TO_IMAGE`, `TEXT_TO_VIDEO`, `IMAGE_TO_VIDEO`), available models, and request/result objects.
  - `GeminiMediaGenerationProvider` uses real REST endpoint `generateContent` with `responseModalities: ["TEXT", "IMAGE"]` and `imageConfig` aspect ratio.
  - Decodes base64 `inlineData` image bytes, saves into app-private storage, inserts `MediaEntity` into Room, and associates with project/chat.
  - Tested against real Gemini endpoint; verified that free tier requires billing for image generation (HTTP 429 RESOURCE_EXHAUSTED with limit 0), cleanly reporting the limitation without faking.
  - `MockMediaGenerationProvider` provides offline mock generation.

- **Production Cloud Sync Adapter (`RestCloudSyncProvider`, `RealSyncRepository`):**
  - Implemented production-ready HTTP/REST cloud sync provider adapter using OkHttp.
  - Enforced conflict resolution strategies (`SERVER_WINS`, `CLIENT_WINS`, `LAST_WRITE_WINS`, `MANUAL`) in `RealSyncRepository`.

- **Dynamic Model Selection UI & Media Generation UX:**
  - `AIProviderSettingsDialog` & `StandaloneAIProviderSettingsDialog`: "Refresh Models" action, loading spinner, error banners, cached badges, model ID and description display.
  - `MediaVaultScreen`: Integrated AI image generation mode with prompt input, model selector, progress indicator, and status banner.

- **Token & Usage Analytics Dashboard (`UsageRecordEntity`, `UsageDao`, `LocalUsageRepository`, `UsageAnalyticsViewModel`, `UsageAnalyticsScreen`):**
  - Persistent Room storage (`usage_records`) recording prompt tokens, completion tokens, total tokens, provider, model, timestamp, request status (`SUCCESS`, `ERROR`), and safe truncated error info without storing sensitive API keys or full prompt texts.
  - High-performance aggregation logic generating `UsageSummary` with KPI metrics (total requests, token counts, success/failure counts, provider breakdown, model breakdown).
  - Time range filtering (`ALL_TIME`, `TODAY`, `LAST_7_DAYS`, `LAST_30_DAYS`).
  - M3 UI with KPI cards, provider and model breakdown lists, recent activity timeline with status chips and error alerts, and history clearing dialog.
  - Auto-recording wired cleanly into `WorkspaceViewModel.sendMessage` and `WorkspaceViewModel.proposeChange`.

- **Media Vault (`MediaEntity`, `MediaDao`, `LocalMediaRepository`, `MediaVaultViewModel`, `MediaVaultScreen`):**
  - Room metadata persistence (`media_items`) tracking filename, media type (`IMAGE`, `VIDEO`), creation timestamp, app-private file path, size in bytes, and project/chat associations.
  - Sandboxed app-private storage (`context.filesDir/media_vault`) with path traversal defense (`..` and slash sanitization, canonical path enforcement).
  - High-fidelity UI: 2-column media grid with Coil `AsyncImage` previews, video thumbnail badges, missing/corrupt file indicators, file rename, deletion from disk and database, and full preview dialog.
  - Media sample creation utility for rapid local testing and asset population.

- **Ideas Vault (`IdeaEntity`, `IdeaDao`, `LocalIdeaRepository`, `IdeasVaultViewModel`, `IdeasVaultScreen`):**
  - Room persistence (`ideas`) capturing title, description, timestamps, status (`DRAFT`, `IN_PROGRESS`, `COMPLETED`, `ARCHIVED`), tags, and project/chat associations.
  - Fast search and status filtering (`All`, `Drafts`, `In Progress`, `Completed`, `Archived`).
  - Direct "Convert to Project" action that instantiates a new project in `ProjectRepository` with the idea title and establishes bidirectional project linking.

- **Cloud Synchronization Foundation (`com.example.sync.*`):**
  - Offline-first architecture where the local Room database remains the authoritative single source of truth when offline.
  - `CloudSyncProvider` interface supporting pluggable cloud backends without touching application domain logic.
  - `LocalOnlySyncProvider` active by default for complete local privacy and offline resilience.
  - `MockCloudSyncProvider` with in-memory remote storage for simulating sync pushes, pulls, and conflict states.
  - `MediaObjectStorageProvider` with `LocalOnlyMediaStorageProvider` abstraction for future binary object uploads without premature vendor lock-in.
  - `SyncMetadataEntity` table and `SyncMetadataDao` tracking sync statuses (`SYNCED`, `PENDING_UPLOAD`, `CONFLICT`, `LOCAL_ONLY`, `ERROR`), remote IDs, timestamps, and error messages.
  - `RealSyncRepository` orchestrating push/pull cycles with configurable conflict resolution strategies (`SERVER_WINS`, `CLIENT_WINS`, `LAST_WRITE_WINS`, `MANUAL`).

- **Unified Product Navigation & Settings (`AppNavigation.kt`, `SettingsScreen.kt`, `StandaloneAIProviderSettingsDialog.kt`, `GitHubSettingsDialog.kt`):**
  - M3 `NavigationBar` across 5 primary product destinations: Projects, Ideas, Media, Analytics, Settings, with active indicator pills and system navigation bar insets.
  - Full-screen Workspace sub-screen with pop-back navigation.
  - Settings screen with standalone AI provider configuration dialog, GitHub account management dialog, cloud sync mode toggle, connection test, and "Sync Now" trigger.

- **Production Hardening, Build Architecture & Keystore Security:**
  - `SocketTimeoutException` and `UnknownHostException` network failure handling unified across AI providers (`GeminiAIProvider`, `OpenAIProvider`, `AnthropicProvider`).
  - Safe keep rules in `app/proguard-rules.pro` for Moshi, Retrofit 2, Room entities, OkHttp, Coroutines, and app packages (`com.example.ai.**`, `com.example.data.**`, `com.example.github.**`, `com.example.sync.**`).
  - Automated CI/CD Cloud Build Pipeline (`.github/workflows/build.yml`) for testing, debug APK, release APK, and release bundle packaging.
  - Configurable `KEY_ALIAS` support in `app/build.gradle.kts` alongside `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`.

- **Unit & Robolectric Test Suite:**
  - **147/147 unit and Robolectric tests passed** (0 failures, 0 errors, 0 skipped across 20 test suites).

- **Build Artifacts Verified Locally:**
  - Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (27 MB)
  - Release APK: `app/build/outputs/apk/release/app-release.apk` (16 MB)
  - Release Bundle (AAB): `app/build/outputs/bundle/release/app-release.aab` (16 MB)

**Pending**
- Expanded cloud sync backends (e.g. Supabase, Firebase Cloud Firestore).
- Upgraded Gemini plan for billing-enabled image generation.

**Errors / Bugs**
- None. All 147 unit tests pass cleanly. Debug APK, Release APK, and Release AAB build without error.

**Verified**
- **Unit & Robolectric Test Suite:**
  - Command: `gradle :app:testDebugUnitTest`
  - Result: 147 tests completed, 0 failures, 0 errors, 0 skipped.
  - Test suites:
    - `com.example.ai.Phase9HardeningTest` (10/10 passed)
    - `com.example.data.UsageAnalyticsTest` (5/5 passed)
    - `com.example.data.MediaVaultTest` (5/5 passed)
    - `com.example.data.IdeasVaultTest` (5/5 passed)
    - `com.example.sync.CloudSyncTest` (4/4 passed)
    - `com.example.ai.ProductionHardeningTest` (4/4 passed)
    - `com.example.ai.AutonomousExecutionEngineTest` (28/28 passed)
    - `com.example.ai.CodeChangeApplierTest` (13/13 passed)
    - `com.example.ai.CodeChangeProposalTest` (4/4 passed)
    - `com.example.ai.AIModelRegistryTest` (4/4 passed)
    - `com.example.ai.AIFactoryTest` (4/4 passed)
    - `com.example.ai.APIKeyManagerTest` (3/3 passed)
    - `com.example.ai.FallbackAIProviderTest` (4/4 passed)
    - `com.example.data.ProjectFileSystemTest` (17/17 passed)
    - `com.example.data.ProjectFileRepositoryTest` (2/2 passed)
    - `com.example.github.GitHubCloneTest` (14/14 passed)
    - `com.example.github.GitHubPushTest` (16/16 passed)
    - `com.example.github.GitHubIntegrationTest` (2/2 passed)
    - `com.example.ExampleRobolectricTest` (1/1 passed)
    - `com.example.ExampleUnitTest` (1/1 passed)
- **Build & Packaging Verification:**
  - Debug APK: `gradle :app:assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk` (BUILD SUCCESSFUL)
  - Release APK: `KEYSTORE_PATH=... STORE_PASSWORD=... KEY_PASSWORD=... KEY_ALIAS=... gradle :app:assembleRelease` -> `app/build/outputs/apk/release/app-release.apk` (BUILD SUCCESSFUL)
  - Release AAB: `KEYSTORE_PATH=... STORE_PASSWORD=... KEY_PASSWORD=... KEY_ALIAS=... gradle :app:bundleRelease` -> `app/build/outputs/bundle/release/app-release.aab` (BUILD SUCCESSFUL)

**Build & Release Guide**
- **To build Debug APK locally:**
  ```bash
  gradle :app:assembleDebug
  ```
- **To build Release APK locally:**
  ```bash
  STORE_PASSWORD=<password> KEY_PASSWORD=<password> gradle :app:assembleRelease
  ```
- **To build Release AAB bundle locally:**
  ```bash
  STORE_PASSWORD=<password> KEY_PASSWORD=<password> gradle :app:bundleRelease
  ```
- **To run unit and Robolectric tests:**
  ```bash
  gradle :app:testDebugUnitTest
  ```

**Git Status & Commit Information**
- Current Commit Hash: `367496679369fe0200cbc83b32751dc1ed521a49`
- Previous Commit Hash: `f92269fadf80777a985e4855c2b7e8924545ebcd`
- Remote Push: Attempted `git push origin main` (requires interactive GitHub PAT credentials in this environment).
- Next Recommended Milestone: Phase 9 — Production Cloud Sync Integration & Real Media Generation Provider Plugin.


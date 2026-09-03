# Decisions

Maintain important architectural and product decisions.

## Verified Decisions

- **Product name is Autonomous Arc.**
- **Mobile-first approach:** The UI is designed specifically for mobile screens utilizing the High Density theme.
- **GitHub is the source of truth for code and documentation.**
- **AI providers should be modular:** Demonstrated by the `AIProvider` interface to prevent vendor lock-in.
- **API keys must never be committed:** Credentials should be handled via the `.env` approach securely.
- **AI code changes should eventually use review/approval:** Emphasized in the roadmap for transparent automation.
- **Important changes should be reversible/versioned:** Reflected in the planned architectural phases.
- **Gemini API Integration:** Selected Direct REST (Option B) with Moshi for the prototyping phase to avoid Firebase backend setup overhead, isolating the logic in `GeminiAIProvider` and exposing a UI toggle to safely fall back to `MockAIProvider`.

## Future Decisions

*(Template for future decisions)*

**Decision:** [What was decided]
**Date:** [YYYY-MM-DD]
**Reason:** [Why this decision was made]
**Alternatives considered:** [What else was evaluated]
**Impact:** [How this affects the project]

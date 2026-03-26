# Antigravity Model Discovery Research

This document outlines the findings when attempting to identify the correct string IDs for AI models within the Antigravity Language Server and the challenges encountered when trying to fetch them dynamically. 
This was discovered in March 2026.

## 1. The Challenge of Dynamic Model Fetching

At first glance, it appears possible to dynamically fetch the full list of available models using the Google Cloud Code `/v1internal:fetchAvailableModels` endpoint (e.g., as done by the "Antigravity Quota Monitor" extension). 

However, direct calls to `https://daily-cloudcode-pa.googleapis.com/v1internal:fetchAvailableModels` require standard Google Cloud OAuth 2 credentials bound to an active Google Cloud Project (e.g. `cloudaicompanionProject` with Cloud Assist API enabled). 

Extracting the `ya29` token cached by VS Code extensions in the SQLite `state.vscdb` local storage fails with a `401 UNAUTHENTICATED` when called externally. Rebuilding dynamic authentication flows requires duplicating complex extension OAuth logic. 

**Conclusion:** Dynamically retrieving the models directly from the Google API requires significant OAuth overhead that breaks the "plug and play" nature of Amaya's backend architecture.

## 2. Defaulting to Hardcoded Enums

To avoid requiring users to authenticate Google Cloud directly, Amaya routes all chat inferences directly through the Antigravity Language Server running locally via gRPC (`localhost:8765`).

The Antigravity backend abstracts models internally using placeholder enums like `MODEL_PLACEHOLDER_M18`. These mapped IDs must be explicitly hardcoded within Amaya's `/src/antigravity-api.ts` file so they visually reflect the proper UI selection.

## 3. Proven Model Mappings

We used a "shotgun test" to directly interrogate the Language Server using placeholders M1 through M30. Based on the responses and quota limits returned by the respective gRPC trajectories, here is the guaranteed mapping array applied to Amaya Remote:

| Remote IDE Label | Internal String Enum Value | Notes / Trajectory Evidence |
| :--- | :--- | :--- |
| **Gemini 3 Flash** | `MODEL_PLACEHOLDER_M18` | Standard Gemini completion |
| **Gemini 3.1 Pro (High)** | `MODEL_PLACEHOLDER_M7` | Previous Gemini 3 Pro fallback |
| **Gemini 3.1 Pro (Low)** | `MODEL_PLACEHOLDER_M8` | Previous Gemini 3 Pro fallback |
| **Claude Opus 4.6 (Thinking)** | `MODEL_PLACEHOLDER_M21` | Caught explicit `claude-opus-4-6-thinking` 429 Quota Exhaustion Error |
| **Claude Sonnet 4.6 (Thinking)** | `MODEL_PLACEHOLDER_M26` | Caught explicit `claude-sonnet` Quota limit |
| **Gemini 2.5 Flash Thinking** | `MODEL_GOOGLE_GEMINI_2_5_FLASH_THINKING` | Default legacy parameter |

### Future-Proofing

If Antigravity releases new AI models, an automated script (like `test-chat.js` or `shotgun-test.js`) must be run to test out placeholders recursively (e.g., `M27`, `M28`) by analyzing the AI's response to identify its version, maintaining the mapping array manually.

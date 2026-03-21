# SOMA Build Prompts — Master Index

## Project: SOMA (Android-Native Agentic AI Assistant)
## Repo: https://github.com/yourowndog/soma
## Last Updated: March 20, 2026

---

## Architecture Documents

| File | Purpose |
|------|---------|
| `SOMA_Architecture_Plan.md` | Full roadmap, UI spec, project structure, risk register |
| `SOMA_ASI_CherryPick.md` | Reference guide for files to study/borrow from ASI repo |
| `deep_research_prompt_v2.md` | Deep research prompt (for Gemini/Claude research mode) |

## Build Prompts (In Order)

| Phase | File | Status | Difficulty | Best Model |
|-------|------|--------|------------|------------|
| 0 | `SOMA_Phase0_Prompt.md` | ✅ DONE | Medium | Any |
| 1 | `SOMA_Phase1_Prompt.md` | ✅ DONE | Medium | Gemini Pro |
| Fix | `SOMA_ToolFix_Prompt.md` | ✅ DONE | Easy | Any free model |
| 1.5 | `SOMA_Phase1.5_Prompts.md` | ✅ DONE | Easy-Medium | Gemini Pro |
| 2 | `SOMA_Phase2_Prompt.md` | ⬜ TODO | Hard | Sonnet/Opus |
| 3 | `SOMA_Phase3_Prompt.md` | ⬜ TODO | Medium | Gemini Pro |
| 4 | `SOMA_Phase4_Prompt.md` | ⬜ TODO | Medium | Gemini Pro |
| 5 | `SOMA_Phase5_Prompt.md` | ⬜ TODO | Medium | Gemini Pro |
| 6 | `SOMA_Phase6_Prompt.md` | ⬜ TODO | Hard | Sonnet |
| 7 | `SOMA_Phase7_Prompt.md` | ⬜ TODO | Hard | Sonnet/Opus |
| 8 | `SOMA_Phase8_Prompt.md` | ⬜ TODO | Hard | Opus |

## Phase Summary

- **Phase 0:** Basic chat + Gemini API + Room persistence ✅
- **Phase 1:** Model switching + OpenRouter + open_app tool (regex hack) ✅
- **Phase 1.5:** Live model list from OpenRouter API, search/filter, free toggle, persist selection ✅
- **Phase 2:** Koog agent loop OR manual function calling, proper tool registry
- **Phase 3:** Android deep integration (intents, settings, volume, flashlight, alarms, foreground service)
- **Phase 4:** Multiple sessions, session list, cross-session memory (save/recall facts)
- **Phase 5:** Dashboard (cost, tools, health), agent profiles, tool call visualization
- **Phase 6:** Voice (TTS, STT, Porcupine wake word, hands-free driving mode)
- **Phase 7:** Fleet (Ollama local models, state sync, SSH commands, QMD memory)
- **Phase 8:** Smart routing, accessibility automation, self-healing, UI polish

## Budget Strategy

| Model Tier | Use For | Cost |
|------------|---------|------|
| Free OpenRouter (StepFun, Gemma, Qwen) | Daily testing, simple tasks | $0 |
| Gemini Flash (direct API key) | Medium tasks, long context | Free tier limits |
| DeepSeek R1 (OpenRouter) | Code + reasoning, cheap | ~$0.55/M tokens |
| Claude Sonnet 4 (OpenRouter) | Complex code, architecture | ~$3/M tokens |
| Opus (Claude.ai subscription) | Architecture decisions, hard debugging | Subscription |

## Key Decisions Log

1. **Engine:** Koog (JetBrains) — but Phase 0-1 uses raw HTTP to de-risk
2. **UI:** Jetpack Compose, dark theme, OpenCode-inspired
3. **Default model:** StepFun Step 2 Free via OpenRouter
4. **Memory:** Room SQLite (Tier 1) → QMD on fleet (Tier 2) → Mem0 cloud (Tier 3)
5. **ASI repo:** Reference only, not forked — cherry-pick tools/patterns
6. **Tool calling:** OpenAI function calling format via OpenRouter (Phase 2)
7. **Voice:** Android SpeechRecognizer + Porcupine + built-in TTS
8. **Fleet:** HTTP API (not direct SSH from phone), Ollama for local models

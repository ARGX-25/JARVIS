# JARVIS — Phase 1: Memory Spine
**Version:** 3.1 | **Status:** Pre-build | **Depends on:** Phase 0 stable

---

## Objective
Persist compressed conversation summaries across sessions. Inject them into Cameron's context on startup so JARVIS remembers who you are and what you're building.

**Win condition:** Close app. Reopen next day. JARVIS references yesterday's conversation without being told.

---

## What It Does Not Do
No vector DB. No semantic search. No cloud sync. No mid-session memory updates. No multi-agent memory sharing. No user-facing memory browser. Summaries only — never raw logs.

---

## Key Decisions

| Decision | Choice | Reason |
|---|---|---|
| What generates summaries | Cameron (Gemini 2.0 Flash) | Semantic understanding beats rule-based extraction |
| Session boundary | One calendar day (`yyyy-MM-dd`) | Natural memory unit for a daily driver |
| Summary trigger | `onStop()` — but only updates, never clears | Accumulates all day's messages across multiple app opens |
| SessionStore lifetime | Lives in CuddyService, not Activity | Must survive multiple open/close cycles in one day |
| Minimum threshold | 2 user messages | Captures short but meaningful sessions; filters greetings-only |
| Summaries loaded at startup | Last 7, hard cap 800 tokens | 7 days of context; oldest trimmed if over cap |
| DB library | Room over raw SQLite | Compile-time query validation, coroutine integration |
| Sort order | Oldest → newest everywhere | One standard, no contradictions |
| Token estimation | `summary.length / 4` | Approximation sufficient for cap enforcement (English-biased) |
| Max DB rows | 90 entries, prune oldest on insert | Bounded storage, no manual cleanup needed |

---

## Architecture

```
onStop() fires
    │
    ├─ isNewDay()? → YES → clearForNewDay() → accumulate fresh
    │               → NO  → continue accumulating
    │
    ├─ hasEnoughContent()? (≥2 user messages)
    │   NO  → skip
    │   YES → send to Cameron with compression prompt
    │           │
    │           ├─ API success → structured summary ≤150 words
    │           └─ API failure → fallback mechanical summary
    │
    └─ INSERT OR REPLACE into Room DB (sessionId = yyyy-MM-dd)
       Prune if rowcount > 90

App opens
    │
    └─ Load last 7 summaries (ASC) on background coroutine
       Enforce 800 token cap — trim oldest until under limit
       Format block oldest→newest
       Prepend to Cameron system prompt
       Session begins with continuity
```

---

## Data Model

```kotlin
@Entity(tableName = "memory_summaries")
data class MemorySummary(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,       // yyyy-MM-dd, unique per day
    val timestamp: String,       // ISO 8601
    val summary: String,         // ≤2000 chars
    val tokenEstimate: Int,      // summary.length / 4
    val generatedBy: String      // "CAMERON"
)
```

Runtime only (never persisted):
```kotlin
data class SessionMessage(val role: String, val content: String, val timestamp: String)
data class MemoryContext(val formattedBlock: String, val isEmpty: Boolean, val totalTokenEstimate: Int)
```

---

## Components

| Class | Lives In | Job |
|---|---|---|
| `MemoryManager.kt` | CuddyService | Single coordinator — onSessionBackground(), loadContextForStartup(), clearAllMemory() |
| `SummaryGenerator.kt` | CuddyService | Calls Cameron with compression prompt. Returns summary string always (fallback if API fails) |
| `ContextInjector.kt` | CuddyService | Formats summaries oldest→newest. Enforces 800 token cap by trimming oldest |
| `SessionStore.kt` | CuddyService | In-memory message accumulator. clearForNewDay() only — never on onStop() |
| `MemoryDatabase.kt` | Database layer | Room DB + DAO. INSERT OR REPLACE. Prune on insert |

---

## Compression Prompt (Cameron)

```
You are a memory compression system for a personal AI assistant.
Compress this conversation into ≤150 words.

Extract only:
- Active projects and their status
- Decisions made
- Tasks assigned or completed
- Preferences stated
- Important names, systems, references

No small talk. No greetings. Output the summary only.
```

---

## Injected System Prompt Format

```
You are JARVIS, a personal AI assistant for Arghya Dutta.

Relevant Memory Context (oldest to newest):
[yyyy-MM-dd]: [summary]
[yyyy-MM-dd]: [summary]

Use this context naturally. Do not announce that you remember things.
```

---

## Edge Cases

| Scenario | Behaviour |
|---|---|
| <2 user messages | Skip summary generation |
| Multiple app opens same day | All messages accumulate — one summary per day (REPLACE) |
| Cameron API down at onStop() | Fallback mechanical summary stored |
| DB unavailable at startup | isEmpty = true — base prompt used — no crash |
| Corrupted summary row | Skip that row — use remaining |
| 7 summaries exceed 800 tokens | Trim oldest until under cap |
| clearAllMemory() called | Wipes Room DB AND SessionStore atomically |
| Clock changes mid-session | sessionId fixed at CuddyService init — unaffected |
| First ever launch | isEmpty = true — no crash |

---

## Invariants
- Raw conversation logs never touch disk
- Memory failure never crashes the app
- DB operations never block the UI thread
- SessionStore clears only on day change — never on onStop()
- clearAllMemory() is always atomic — DB and SessionStore together
- Sort order is always oldest→newest — no exceptions
- Injected context never exceeds 800 tokens — enforced in code

---

## Definition of Done
- [ ] Room DB initialises without errors
- [ ] SessionStore accumulates across multiple opens — verified with 3-open test
- [ ] clearForNewDay() never fires on onStop()
- [ ] Summary generated using Cameron at each backgrounding
- [ ] 2-message session summarised; 1-message session skipped
- [ ] REPLACE strategy verified — same day = one row
- [ ] Fallback summary stored when API unavailable
- [ ] Last 7 summaries loaded at startup, non-blocking
- [ ] Memory block formatted oldest→newest
- [ ] 800 token cap enforced — tested with 10 summaries
- [ ] Cameron references yesterday's content without being told
- [ ] clearAllMemory() wipes DB and SessionStore
- [ ] No memory failure crashes the app
- [ ] No raw logs on disk anywhere
- [ ] Phase 0 JSONL logs imported into Room DB

---

*"Memory is not storage. Memory is relevance compressed over time."*
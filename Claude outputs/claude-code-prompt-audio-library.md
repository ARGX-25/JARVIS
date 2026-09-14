# JARVIS — Pre-rendered audio library + file organisation

## Context

Project root: `P:\Coding\App Development\MyProjects\JARVIS`
The GPT-SoVITS cloned voice is working. This task uses it to render a library of fixed
phrases as wav files, and to tidy up the audio files produced so far.

Two jobs: **(A)** organise what exists, **(B)** generate the phrase library.

---

## A. Organise existing output

Audio files from the cloning work are currently scattered (training samples, test renders,
sliced segments, separated stems). Reorganise into a clear structure and tell me what you
moved. Do not delete anything without listing it first.

```
audio/
├── source/            raw reference material (the original mp3)
├── training/
│   ├── separated/     vocal stems from UVR5/Demucs
│   ├── segments/      sliced, diarized, filtered clips used for training
│   └── rejected/      segments dropped (overlap, music bleed, too short)
├── samples/           test renders used to judge clone quality
└── library/           the phrase library from part B
    ├── wake/
    ├── thinking/
    ├── loading/
    ├── working/
    ├── greetings/
    ├── acknowledgements/
    ├── errors/
    └── nudges/
```

---

## B. Generate the phrase library

Render every line below with GPT-SoVITS.

### Generation rules

- **Use the exact same reference clip and inference parameters as the live TTS path.**
  Cached and live speech must sound like the same person; a different reference clip will
  make them sound like two.
- 24 kHz mono wav (match whatever the live path outputs).
- Normalise loudness across the whole library so no clip is noticeably louder than another.
- Trim leading/trailing silence tightly — dead air at the start defeats the purpose.
- **Duration caps.** Reject and re-render anything over the cap:
  - `wake` ≤ 1.0 s · `thinking` ≤ 1.5 s · `acknowledgements` ≤ 1.5 s
  - `loading` ≤ 3.0 s · `working` ≤ 2.0 s · `greetings` ≤ 2.5 s
  - `errors` ≤ 4.0 s · `nudges` ≤ 5.0 s
- Listen-check pass: flag any clip with audible artifacts, clipping, or wrong prosody so I
  can review them rather than discovering them in use.

### Manifest

Write `audio/library/manifest.json`:

```json
{
  "voice_model": "<checkpoint used>",
  "reference_clip": "<path>",
  "params": { "temperature": 0.0, "top_k": 0, "top_p": 0.0 },
  "generated_at": "<ISO-8601 with timezone>",
  "categories": {
    "thinking": [
      { "id": "thinking_001", "file": "thinking/thinking_001.wav",
        "text": "One moment, Sir.", "duration_s": 0.9 }
    ]
  }
}
```

Code looks up by category, never by filename. The `text` field lets me regenerate the whole
library later against a better voice checkpoint without retyping anything.

---

## The lines

### wake/ — acknowledgement that he heard you, plays instantly

```
Sir.
Yes, Sir.
Sir?
I'm listening.
Go ahead, Sir.
At your service.
Indeed, Sir?
Yes?
```

### thinking/ — short stall while Cameron generates

```
One moment, Sir.
Give me a moment, Sir.
Let me consider that.
Let me reconcile that, Sir.
A moment.
Let me work that through.
Considering, Sir.
Just a moment, Sir.
Let me see.
Bear with me, Sir.
Allow me a moment.
Thinking, Sir.
```

### loading/ — longer stall while a model loads or swaps

```
Stand by, Sir.
One moment — I'm bringing rather more capacity to bear.
Let me consult someone better qualified, Sir.
This warrants more thought than I have to hand. A moment, Sir.
Waking a colleague, Sir.
I'll need a few seconds to spin something up, Sir.
That deserves proper attention. One moment.
Reaching for heavier tools, Sir.
```

### working/ — tool use, lookup, retrieval

```
Checking now, Sir.
Looking into it.
Let me have a look, Sir.
Retrieving that now.
One moment while I check.
Searching, Sir.
Let me find that for you.
Just checking, Sir.
```

### greetings/

```
Good morning, Sir.
Good afternoon, Sir.
Good evening, Sir.
Welcome back, Sir.
Morning, Sir.
You're up early, Sir.
Late night, Sir?
Good morning. You've slept rather well, it seems.
```

### acknowledgements/

```
Very well, Sir.
Of course.
Right away, Sir.
Consider it done.
As you wish, Sir.
Certainly.
Done, Sir.
Noted.
```

### errors/ — honest failure, in character

```
I'm afraid I can't reach my faculties at the moment, Sir.
I'm afraid that's beyond my reach just now, Sir.
Something's gone wrong at my end, Sir. I'll keep trying.
I'm afraid I don't have access to that at the moment.
My apologies, Sir — that service isn't responding.
I've lost my voice, it seems, Sir.
I'm afraid I can't get to that right now, Sir.
```

### nudges/ — proactive reminders

```
Sir, it's time for the gym.
Sir, have you written your ML assignment?
You've been on YouTube rather a long while, Sir.
I think you need a break, Sir.
You've been at that for three hours, Sir. A pause might serve you well.
Sir, the gym. Unless you'd rather I pretended not to notice.
That assignment hasn't moved since Tuesday, Sir.
A short walk might do you good, Sir.
```

---

## C. Playback contract (for Cuddy)

Not audio work — the code side of using this library. Implement in the Cuddy server.

- **Latency-triggered, not unconditional.** Play a `thinking` clip only if the first token
  hasn't arrived within **700 ms**. Fast answers must not be slowed down by a filler.
- **Never repeat consecutively.** Track the last-played id per category and exclude it from
  the next random draw. With 8–12 lines per category this is enough to stay unobtrusive.
- **Category is chosen by what's actually happening:** generating → `thinking`; acquiring the
  VRAM lock / loading a model → `loading`; calling a tool → `working`; downstream service
  unreachable → `errors`.
- **Barge-in.** If the real response is ready while a filler is still playing, let the filler
  finish the current clip rather than cutting it off mid-word — clipped speech sounds broken.
  This is why the duration caps above matter.
- Log every filler played to the event log (`type: "filler"`) with the category and the
  measured wait it covered, so I can tell later whether they're firing too often.

---

## Reporting

List what you moved during part A, what you generated in part B, total library duration, and
any clips you'd flag for re-rendering. Be straight about quality — if a category came out
noticeably worse than the rest, say so rather than letting me find it at nine in the morning.

# MicroCodeDrills: Plan

Copyright (c) 2026 John Blanton. All rights reserved. See LICENSE.

## What it is
A local app that shows one tiny coding drill at a time: a full, runnable program with a single piece
blanked out and a one-line prompt that tells you exactly which variant to write. You fill the blank and
press Submit. Correct: next drill. Wrong: a pop-up shows the expected code and a short explanation of why,
then moves on. Nothing leaves your machine and nothing calls an AI at runtime.

## Decisions
| Topic | Decision |
|---|---|
| App language | Java 21, no framework needed for v1 (JDK `com.sun.net.httpserver`), browser UI served on localhost |
| Launch | `java Serve.java` from the project folder (the Java counterpart of `python serve.py`) |
| Packs | One folder per question set under `packs/`, named in Java style: `JavaCodeTestMicroDrills`, `SqlCodeTestMicroDrills`; a Python pack would be `PythonCodeTestMicroDrills` |
| Grading | Execution based: the answer is spliced into the template, compiled and run, and the harness prints `PASS` |
| Variants | The prompt alone must identify the variant; `mustContain` rules can require a specific call so a different working variant counts as wrong |
| Review queue | Deferred, not in v1 |
| License | All rights reserved; repository publicly visible as a portfolio piece |

## Pack format
```
packs/<PackId>/
  pack.json          id, title, language, sourceFile, blankMarker, compile/run commands, timeout, topics
  drills/<topic>/NNN.json
  runner/            optional helper scripts used by run commands
```
Drill file: `id, topic, prompt, given, template, answer, why, mustContain`. The template (kept server-side, never shown) holds the whole program
with the line `@@BLANK@@` where the learner's code goes. Run commands may use `{file}`, `{outDir}`, `{packDir}`.
Adding a language means adding a folder; no app code changes.

## Phases
1. Repo skeleton, pack format, scrubbed export of all drills (done).
2. Java runner and grader: splice, compile, run with timeout in a child process, localhost-only server.
3. Web UI: drill screen, Submit and Ctrl+Enter, result pop-up with answer and why, pack and topic pickers, local progress file.
4. Write the `why` text for every drill and tighten variant prompts and `mustContain` rules.
5. README with screenshots, authoring guide, CI that checks every answer passes and every blank fails, Python and extra packs.

## Status
Phases 1 to 5 are complete. Deferred: review queue and spaced repetition, more packs.

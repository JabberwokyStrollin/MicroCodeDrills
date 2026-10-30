# Authoring packs

A pack is a folder under `packs/`. Named in Java style: `<Language>CodeTestMicroDrills`, for example
`PythonCodeTestMicroDrills`. The app loads every folder that contains a `pack.json`; no app code changes.

## Layout
```
packs/<PackId>/
  pack.json
  drills/<section-id>/NNN.json
  runner/            optional helper scripts
```

## pack.json
| Field | Meaning |
|---|---|
| `id`, `title`, `language` | Identity and display name |
| `sourceFile` | File name the spliced program is written to (`Drill.java`, `drill.py`) |
| `blankMarker` | Text that marks the blank in templates (`@@BLANK@@`) |
| `compile` | Optional command list, run first. Skip for interpreted languages |
| `run` | Command list that runs the program. It must print `PASS` when correct |
| `timeoutSeconds`, `passToken` | Run limit and the success text (default `PASS`) |
| `sections` | `[{id, title}]` in display order. Each id is a folder under `drills/` |

Command lists may use `{file}` (the written source file), `{outDir}` (a temp folder) and `{packDir}`.

## A drill file
```json
{
  "id": "001",
  "section": "collections",
  "prompt": "One or two sentences. Name the exact variant to write.",
  "template": "...full program...\n    @@BLANK@@\n...",
  "answer": "the reference code for the blank",
  "why": "One or two sentences on why it is written that way.",
  "mustContain": null
}
```
- Put `@@BLANK@@` alone on its own line; the app indents every answer line to match.
- The program must print `PASS` only if all checks succeed, and must not pass with the blank empty.
- `mustContain` (optional) is text the answer must include, ignoring whitespace. Use it when the prompt names a
  variant and other working forms should count as wrong.
- Ids sort as text, so zero-pad them. They set the drill order.

## Test your pack
```
java Serve.java --verify
```
Every answer must pass and an empty blank must fail.

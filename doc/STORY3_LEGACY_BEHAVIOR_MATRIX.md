# Story 3 Legacy Behavior Matrix (frozenApp Reference)

Scope: replicate legacy Android-only functional behavior for measurement run progress and completion UI before introducing UX changes.

Reference files:
- `frozenApp/src/main/java/edu/gatech/cc/cellwatch/domain/fcc/MeasurementService.kt`
- `frozenApp/src/main/java/edu/gatech/cc/cellwatch/ui/main/MeasureViewModel.kt`
- `frozenApp/src/main/java/edu/gatech/cc/cellwatch/ui/main/MeasureFragment.kt`
- `frozenApp/src/main/res/values/strings.xml`

## State Transition Matrix

| Legacy source event | Legacy view-model transition | Required shared behavior |
| --- | --- | --- |
| `STARTED` | `PRE -> START`; create empty `MeasurementGroup` with `groupId`; clear error | Keep deterministic start transition and group id initialization |
| `LOCATE` | `progress = LOCATE` | Expose locating stage |
| `LATENCY` | `progress = LATENCY` | Expose latency stage |
| Latency measurement available | `results.latency = measurement` | Persist/retain latency artifact in run state |
| `DOWNLOAD` | `progress = DOWNLOAD`; may apply latest latency first | Expose download stage; keep latency result retained |
| Download measurement available | `results.download = measurement` | Persist/retain download artifact in run state |
| `UPLOAD` | `progress = UPLOAD`; may apply latest download first | Expose upload stage; keep prior artifacts retained |
| Upload measurement available | `results.upload = measurement` | Persist/retain upload artifact in run state |
| `DONE` with non-null group | `progress = END`; set final group; clear error | Terminal success state with full group |
| `DONE` with null group + `errorCode == 429` | `progress = ERROR`; set explicit rate-limit message | Preserve exact user-facing 429 guidance |
| `DONE` with null group + other error | `progress = ERROR`; use service error text or default fallback | Preserve deterministic fallback error message |
| Async upload-time resolution after success | state `uploadTime` updated later | Keep upload-time update as separate post-complete update |

## Header/Text Mapping Matrix

| Progress | Legacy UI header |
| --- | --- |
| `PRE`, `START` | `Measuring` |
| `LOCATE` | `Finding server` |
| `LATENCY` | `Measuring latency` |
| `DOWNLOAD` | `Measuring download speed` |
| `UPLOAD` | `Measuring upload speed` |
| `END` | `Measurement complete` |
| `ERROR` | `Measurement failed` (+ `: <message>` when present) |

## Visibility Rules

| Condition | Legacy UI behavior |
| --- | --- |
| In progress (`START/LOCATE/LATENCY/DOWNLOAD/UPLOAD`) | Show progress bar; hide completion actions |
| Terminal (`END/ERROR`) | Hide progress bar; show completion actions |

## Notes for Migration

- Keep behavior parity first; do not introduce new stages or new user-facing error categories in Story 3 parity pass.
- Shared layer should own state transitions and header mapping; platform layer should only render and bind OS-specific execution.
- Debug-only details (reason-code traces) should stay out of product-facing UI-flow assertions.

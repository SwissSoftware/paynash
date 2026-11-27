# Proposal

## Why

Four changes are implemented and tested automatically but still have open tasks that need real hardware, real devices, or written guides: `add-pos-ordering-system`, `harden-printing-for-events`, `printer-category-routing`, and `add-salon-mode`. These tasks block archiving, and archiving is needed so their specs become main specs that `configurable-business-features` can build on. Most of them also depend on printing with a real Epson printer, which does not work yet (`fix-epos-http-version` is still open).

This change collects the 19 open tasks in one place, so the four changes can be archived without losing them.

## What Changes

- The open tasks move here unchanged, grouped by kind, each with its origin (e.g. "from `add-pos-ordering-system` 5.8"):
  - hardware tests with a real Epson TM-m30III,
  - setup and operations guides (`docs/printing.md`, `docs/operations.md`, `docs/event-setup.md`, `docs/salon-setup.md`),
  - on-site acceptance runs recorded in `docs/acceptance.md`,
  - the Android and iOS apps (targets, secure token storage, builds, distribution),
  - the remaining automated test runs.
- In the four origin changes, each moved task gets the note "→ moved to hardware-acceptance". Their checkboxes stay open, so nothing looks tested that was not.
- No requirement changes. The behaviour these tasks verify is already specified by the origin changes, so this change has no spec deltas (`skip_specs`).

## Capabilities

### New Capabilities
- none

### Modified Capabilities
- none

## Impact

- Docs: `docs/printing.md`, `docs/operations.md`, `docs/event-setup.md`, `docs/salon-setup.md`, `docs/acceptance.md`.
- App: Android and iOS targets, secure token storage, build and signing configuration.
- Possibly `EposClient` and the receipt rendering, if the hardware test shows code page or status problems.
- Order of work: the printer tasks can only pass once `fix-epos-http-version` prints on the real printer. `docs/salon-setup.md` and `docs/event-setup.md` are rewritten as template guides by `configurable-business-features` (task 5.1), so write them after that change or update them there.

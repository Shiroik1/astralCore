# Project working agreements

- Ask the project owner for explicit approval before running tests, smoke checks, gameplay verification, benchmarks, or package verification. Do not run them automatically after changes or restore the removed test suite without being asked.
- Read-only code and asset-reference review is allowed during cleanup. Report clearly when changes have not been tested.
- Keep runtime assets in `res/`, world authoring data in `maps/`, and reference/unreviewed art in `assets/reference/`. See `docs/assets.md`.
- Preserve reusable reference art and seasonal variants unless the owner asks to remove them. Remove confirmed obsolete assets and duplicate export archives rather than inferring that every currently unplaced asset is unwanted.

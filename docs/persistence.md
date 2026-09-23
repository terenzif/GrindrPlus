# Persistence (terenzif fork)

Short map of where GrindrPlus stores data. Full hardening plan:
store `docs/pre-5.0-stability-db-plan.md` + audit `internal/db-crash-stability-audit.md`.

## Own Room DB (`GPDatabase`)

| | |
| --- | --- |
| Class | `com.grindrplus.persistence.GPDatabase` |
| File name | `grindrplus.db` (version **6**) |
| Schema export | `app/schemas/` (`exportSchema = true`) |
| Migration | `MIGRATION_5_6` → creates `block_events` |
| Tests | `GPDatabaseMigrationTest` (5→6 preserves album/phrase rows) |

**Process split:** the same class/name is opened in **two sandboxes**:

- **Grindr process** (module): albums, phrases, teleport
- **Manager process** (`BridgeService`): block events UI

`reset_database` in settings clears only the module-side DB.

## Host Grindr SQLite

`DatabaseHelper` opens `*grindr_user*.db` in the Grindr app process.

**Policy (Track C soft-fail):**
- Missing DB / lock / I/O → empty list, `-1` / `0`, or `false` + log — **never** throw into hooks
- Reads: `OPEN_READONLY`
- Writes: short `OPEN_READWRITE` (still races with Grindr; prefer HTTP/API mutations when possible)

See `isUserDatabasePresent()` for pre-login checks.
## Config

Manager external files: `grindrplus.json` via bridge (not Room). Atomic write is a planned fix.

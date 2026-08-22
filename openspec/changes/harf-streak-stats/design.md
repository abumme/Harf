## Context

See proposal.md — Why, and `docs/04-architecture-sketch.md` §3 (event-sourced streaks). Builds on `harf-foundation` (DI, settings, MVI) and `harf-gameplay` (round lifecycle). Offline.

## Goals / Non-Goals

**Goals:**
- Durable, append-only result log; streak/stats derived by replay.
- In-progress round survives process death.
- Data model ready to be server-mirrored later without a rewrite.

**Non-Goals:**
- Server sync, streak-at-risk push, gifting/repair events (post-launch/backend).
- Leaderboards/leagues.

## Decisions

**Persistence: KSafe serializable data for everything; no separate DB.**
Both the finished-round log and the in-progress round are stored via KSafe (from `harf-foundation`) as `@Serializable` values — the log is a `List<ResultRecord>`, the in-progress round a single blob. The log is tiny (~one record per language per day, low hundreds per year), so a real database's query/scale benefits don't pay for their weight. Alternatives Room / SQLDelight — rejected for the MVP: added dependency + boilerplate for data that fits comfortably in a serializable list. If the log ever outgrows this (unlikely pre-launch), swapping the repository's backing store to a DB is a contained change behind its interface.

**Event-sourced, replay-derived streaks.**
Streak/stats are pure functions over the ordered log; nothing stores a counter. The log is the event vocabulary; later, server "streak-repair" events (gifting) insert into the same conceptual log and replay absorbs them — designed now, implemented later. Alternative: mutable streak counter — rejected (docs call this out as the bug-prone path).

**Uniqueness enforced in the repository.**
One record per (language, puzzle-day). The repository de-duplicates on append (ignore if a record for that language-day exists), since KSafe holds a plain list with no DB unique index. Recording is safe to call once per finished round; keeps replay correct and a future sync idempotent.

**Per-language streaks, using each language's rollover.**
Streaks are computed per language (a separate current/best per language), and "consecutive days" is measured in that language's fixed timezone (from `daily-puzzle`), so a streak aligns with when the player actually gets each language's word. A player keeps independent streaks for uz/ru/en/kk.

**Gameplay integration is a thin hook.**
`harf-gameplay` calls `resultLog.record(...)` on finish and `roundStore.save/restore(...)` during play. No scoring/flow change lives here.

## Risks / Trade-offs

- **Clock/timezone edge cases at rollover could mis-count a streak.** → Compute day-numbers in the language's fixed zone consistently for both the log and the streak rule; unit-test around midnight boundaries.
- **KSafe list read/rewrite on every append is O(n).** → n is tiny (low hundreds/year); acceptable. Keep the whole log in KSafe's in-memory cache and rewrite on append.
- **Serialized schema drift (log records / in-progress blob).** → Version the serialized shapes; on unknown version, discard in-progress (safe: only today's unsaved progress lost) and best-effort migrate the log.

## Migration Plan

Additive, greenfield data. No migration at MVP; serialized shapes are v1 and versioned for forward evolution.

## Open Questions

- None blocking. Streaks are per-language and storage is KSafe (both decided).

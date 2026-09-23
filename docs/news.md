# News (terenzif/GrindrPlus)

In-app News tab + README news entry point for this fork.

## Default link (current)

**GitHub Wiki** — https://github.com/terenzif/GrindrPlus/wiki

Code: `Constants.NEWS_PAGE_URL` / `Constants.NEWS_WIKI_URL` both point at the wiki.

Releases (APKs): https://github.com/terenzif/GrindrPlus/releases

## In-app News tab behavior

1. **Wiki card** — opens `NEWS_PAGE_URL`.
2. **Releases list** — `GET https://api.github.com/repos/terenzif/GrindrPlus/releases` (`NewsViewModel`). Soft-fail on network/parse.
3. **No Telegram message feed** — the old chat-bubble UI (`tgMessages` from Telegram-shaped `news.json`) is no longer the News UI.

## `news.json` (optional push ping only)

Repo root `news.json` remains for `BridgeService` / `fetchNotifs` (periodic soft-fail ping). Schema is still `{message_id, text, date}` for compatibility, but content is **fork-maintained**, not a Telegram mirror. Include `#push` in `text` to trigger a device notification when the id changes.

CI workflow `news.yml` still soft-skips without `TELEGRAM_BOT_TOKEN` (fork-friendly).

## Wiki pages

- [[Home]](https://github.com/terenzif/GrindrPlus/wiki) — status, target Grindr version, links
- [[News]](https://github.com/terenzif/GrindrPlus/wiki/News) — short pointer to Releases

## What changed vs upstream

- No `t.me/grindrplusci` / R0rt1z2-centric news entry points in the manager UI.
- News UI = wiki + GitHub Releases (not Telegram).
- Raw URLs use `terenzif/GrindrPlus` (correct casing).

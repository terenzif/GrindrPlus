# News (terenzif/GrindrPlus)

This is the **fork news stub** for the in-app News tab and README.

## Default link (current)

**GitHub Releases** — https://github.com/terenzif/GrindrPlus/releases

Chosen because the GitHub **wiki** is enabled but has no pages yet (`.wiki.git` cannot be pushed until a maintainer creates the first page in the GitHub UI). Once that exists, prefer:

- Wiki Home: https://github.com/terenzif/GrindrPlus/wiki
- Wiki News: https://github.com/terenzif/GrindrPlus/wiki/News

In code: `Constants.NEWS_PAGE_URL` (Releases) and `Constants.NEWS_WIKI_URL` (wiki).

## What changed vs upstream

- No more `t.me/grindrplusci` / R0rt1z2-centric news entry points in the manager UI.
- Optional `news.json` Telegram mirror still works **if** `TELEGRAM_BOT_TOKEN` is configured; otherwise the Actions job soft-skips.
- Spline / Play scrape / manifest raw URLs use `terenzif/GrindrPlus` (correct casing).

## Maintainer: activate wiki (one-time)

1. Open https://github.com/terenzif/GrindrPlus/wiki and create **Home**.
2. Paste a short status (target Grindr version, Releases link, mapping-packs link).
3. Optionally add a **News** page.
4. Flip `Constants.NEWS_PAGE_URL` to `NEWS_WIKI_URL` in a follow-up PR.

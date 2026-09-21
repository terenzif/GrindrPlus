# News (terenzif/GrindrPlus)

In-app News tab + README news entry point for this fork.

## Default link (current)

**GitHub Releases** — https://github.com/terenzif/GrindrPlus/releases

Code: `Constants.NEWS_PAGE_URL` (Releases) and `Constants.NEWS_WIKI_URL` (wiki).

Wiki is **enabled** (`has_wiki: true`) but the `.wiki.git` repo does **not** exist until a maintainer creates the first page in the GitHub UI. API/git push cannot initialize it (`Repository not found`).

## One-click wiki init (maintainer)

1. Open https://github.com/terenzif/GrindrPlus/wiki/_new (or Wiki → “Create the first page”).
2. Title: `Home`
3. Paste the body from the stub below → Save.
4. Optionally add a `News` page linking to Releases.
5. Open a tiny PR: set `Constants.NEWS_PAGE_URL = Constants.NEWS_WIKI_URL` (or the literal wiki URL).

### Home.md stub (copy-paste)

```markdown
# GrindrPlus (terenzif fork)

Personal / lab fork maintained by [@terenzif](https://github.com/terenzif).

## Current target

- Grindr **26.16.1** (`versionCode` 179451)
- Module APKs: [Releases](https://github.com/terenzif/GrindrPlus/releases)
- Remote mapping packs: [mapping-packs/](https://github.com/terenzif/GrindrPlus/tree/master/mapping-packs)

## Links

| | |
| --- | --- |
| Source | https://github.com/terenzif/GrindrPlus |
| Issues | https://github.com/terenzif/GrindrPlus/issues |
| CI | https://github.com/terenzif/GrindrPlus/actions |

Upstream [R0rt1z2/GrindrPlus](https://github.com/R0rt1z2/GrindrPlus) is archived — news and downloads for this fork live here.
```

## What changed vs upstream

- No `t.me/grindrplusci` / R0rt1z2-centric news entry points in the manager UI.
- Optional `news.json` Telegram mirror only if `TELEGRAM_BOT_TOKEN` is set.
- Raw URLs use `terenzif/GrindrPlus` (correct casing).

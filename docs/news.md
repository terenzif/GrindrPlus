# News (terenzif/GrindrPlus)

## In-app News tab

1. Card → **wiki News** — https://github.com/terenzif/GrindrPlus/wiki/News  
2. Body → fork announcements from repo `news.json` (not GitHub Releases)  
3. **Home** keeps Releases / contributors — News must not duplicate that list  

Code: `Constants.NEWS_PAGE_URL` → wiki/News · `NewsViewModel` loads `CHANNEL_PING_URL`.

## Wiki

[[News]](https://github.com/terenzif/GrindrPlus/wiki/News) — first post summarizes fork WIP (remote packs, CI, LSPatch/Play, stability).

## Debug note (2026-09-24 termbin)

Play LSPatch install on Razr: dispenser **HTTP 403** (Cloudflare). Fixed in-app with UA rotation; reliable path remains Custom Files until Play auth succeeds.

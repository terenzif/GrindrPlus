# Analytics (this fork) — GoatCounter

Optional, **opt-in**, **repo-controlled**. Collector: [GoatCounter](https://www.goatcounter.com/) (FOSS, public dashboard by default). Not upstream Plausible / `gmmz.dev`.

## Control plane

| File | Role |
| --- | --- |
| [`analytics.json`](../analytics.json) | `enabled`, `provider`, `site`, `public_url` |
| Settings → **Opt-in analytics** | User local toggle (default **off**) |

App loads:

`https://raw.githubusercontent.com/terenzif/GrindrPlus/refs/heads/master/analytics.json`

Hits are sent **only if**:

1. User enables opt-in in Settings  
2. `analytics.json` has `"enabled": true`  
3. `"provider": "goatcounter"`  
4. `"site"` is a non-empty `https://….goatcounter.com` origin  

The app GETs `{site}/count?p=…` (tracking pixel). **No API token** in the APK or in this JSON.

## Live board (this fork)

Public dashboard: **[https://grindr-plus.goatcounter.com](https://grindr-plus.goatcounter.com)**

The **Home** tab shows live counters (total, this week, `/home`) from GoatCounter’s
public visitor-counter JSON (`/counter/TOTAL.json`, …) plus a link to the dashboard —
no API token. In GoatCounter settings, keep **“Allow adding visitor counts on your
website”** enabled (required for those URLs). Stats on Home are read-only and do
**not** require the user opt-in toggle (sending hits still does).

## What is sent (when enabled)

| Kind | GoatCounter path / event |
| --- | --- |
| App open | `/home` (+ `android_version` in query) |
| First launch | `/first_launch` |
| Install / clone start | `/install-…` page path |
| Success / fail / cancel | events `install_…_success` / `_failed` / `_cancelled` |

No Grindr profile IDs, chats, or Google accounts.

## Privacy blurb (README / public page)

> Anonymous, opt-in manager metrics (app opens, install success/fail, Android version). No Grindr profiles, chats, or Google accounts. Live board: *(your public_url)*. Config: [`analytics.json`](../analytics.json).

## Not the same as

- **Disable analytics** hook — blocks *Grindr’s* Braze/Firebase inside the patched app.  
- GitHub Insights — repo clones/views only.

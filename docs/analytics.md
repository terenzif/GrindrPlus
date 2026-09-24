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

## What you (maintainer) must do

1. Open [https://www.goatcounter.com/signup](https://www.goatcounter.com/signup) and create a free account.  
2. Create a **site** and pick a code, e.g. `grindrplus-terenzif` → dashboard  
   `https://grindrplus-terenzif.goatcounter.com`  
3. In GoatCounter settings, keep the site **public** (default on hosted GoatCounter) so anyone can view stats without login.  
4. On `master`, set `analytics.json`:

```json
{
  "enabled": true,
  "provider": "goatcounter",
  "site": "https://grindrplus-terenzif.goatcounter.com",
  "public_url": "https://grindrplus-terenzif.goatcounter.com",
  "docs": "https://github.com/terenzif/GrindrPlus/blob/master/docs/analytics.md"
}
```

5. Link `public_url` from README / wiki News.  
6. Optional: in the app, enable **Opt-in analytics** once and open the manager — you should see a `/home` hit on the GoatCounter dashboard within a minute.

Until step 4 is merged to `master`, the app stays quiet even if a user toggles opt-in.

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

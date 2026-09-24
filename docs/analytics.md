# Analytics (this fork)

In-app usage events are **optional** and controlled from this repository — not from upstream `plausible.gmmz.dev`.

## Control plane

| File | Role |
| --- | --- |
| [`analytics.json`](../analytics.json) | Remote config: `enabled`, `host`, `domain`, optional `public_url` |
| Settings → **Opt-in analytics** | User must also enable locally (default **off**) |

App loads:

`https://raw.githubusercontent.com/terenzif/GrindrPlus/refs/heads/master/analytics.json`

Telemetry is sent **only if** all of these hold:

1. User toggled opt-in on in Settings  
2. `analytics.json` has `"enabled": true`  
3. `host` and `domain` are non-empty (your own Plausible-compatible API base URL + site domain)

Otherwise the Plausible client is never started — **no requests**.

## Public analytics site (what you asked for)

GitHub cannot host the *collector* (no pageview API). It **can** be the index: put the public dashboard URL in `analytics.json` → `public_url`, link it from the README/wiki, and keep the secrets (admin login) off GitHub.

### Recommended paths

| Option | Public by default? | Fits this app? | Notes |
| --- | --- | --- | --- |
| **[Plausible](https://plausible.io/)** Cloud or self-host | Shared link / “Make stats public” | **Yes** — SDK already talks Plausible | Same protocol as before; you own the dashboard |
| **[GoatCounter](https://www.goatcounter.com/)** | Yes (sites are shareable) | Needs a tiny adapter or keep Plausible | Great for FOSS public counters; not drop-in for current Android SDK |
| **[Umami](https://umami.is/)** | Can share dashboard | Needs adapter | Nice UI; not Plausible wire format |
| GitHub Insights / Releases download counts | Public | Repo only | Clones/APK downloads ≠ in-app opens |

**Practical pick for this fork:** stay on **Plausible** (SDK already wired), enable a **public shared dashboard**, put that URL in `public_url`.

### Setup checklist (Plausible → public)

1. Create a site in Plausible (Cloud or [self-host](https://plausible.io/docs/self-hosting)) you control.  
   - Example domain field: `grindrplus.terenzif.dev` (any hostname you register in Plausible; it does **not** need to resolve as a real website for the Android client).
2. API base URL ends with `/api/` (same shape as the old upstream host).  
   - Cloud: `https://plausible.io/api/`  
   - Self-host: `https://analytics.YOURDOMAIN/api/`
3. In Plausible: **Visibility → Make stats public** (or create a **Shared link** without password).  
4. Commit on `master`:

```json
{
  "enabled": true,
  "host": "https://plausible.io/api/",
  "domain": "grindrplus.terenzif.dev",
  "public_url": "https://plausible.io/share/grindrplus.terenzif.dev?auth=YOUR_SHARE_TOKEN",
  "docs": "https://github.com/terenzif/GrindrPlus/blob/master/docs/analytics.md"
}
```

5. Link `public_url` from README / wiki News so anyone can open the live board without login.  
6. Users who want to contribute still flip **Opt-in analytics** in Settings.

Until `enabled` + `host` + `domain` are set, leave telemetry off — the public page can exist empty, or you wait to publish the share link after the first opt-in traffic.

### Privacy copy (for the public page / README)

Suggested one-liner:

> Anonymous, opt-in manager metrics (app opens, install success/fail, Android version). No Grindr profiles, chats, or Google accounts. Controlled via [`analytics.json`](../analytics.json).

## What would be sent (when enabled)

Same lightweight events as before (page/event style, not Grindr account data):

- App open (`app://grindrplus/home`) + Android SDK version  
- First launch  
- LSPatch install success / fail / cancel (+ error string on fail)

## How to turn it on for the project

1. Host Plausible (or compatible) as above.  
2. Edit `analytics.json` on `master` with `enabled`, `host`, `domain`, and ideally `public_url`.  
3. Users opt in under Settings.

## Not the same as

- **Disable analytics** hook — that blocks *Grindr’s* Braze/Firebase/etc. inside the patched app.  
- GitHub Insights — only covers this repo’s clones/views, not manager installs on devices.

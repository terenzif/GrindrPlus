# Analytics (this fork)

In-app usage events are **optional** and controlled from this repository — not from upstream `plausible.gmmz.dev`.

## Control plane

| File | Role |
| --- | --- |
| [`analytics.json`](../analytics.json) | Remote config: `enabled`, `host`, `domain` |
| Settings → **Opt-in analytics** | User must also enable locally (default **off**) |

App loads:

`https://raw.githubusercontent.com/terenzif/GrindrPlus/refs/heads/master/analytics.json`

Telemetry is sent **only if** all of these hold:

1. User toggled opt-in on in Settings  
2. `analytics.json` has `"enabled": true`  
3. `host` and `domain` are non-empty (your own Plausible-compatible API base URL + site domain)

Otherwise the Plausible client is never started — **no requests**.

## What would be sent (when enabled)

Same lightweight events as before (page/event style, not Grindr account data):

- App open (`app://grindrplus/home`) + Android SDK version  
- First launch  
- LSPatch install success / fail / cancel (+ error string on fail)

## How to turn it on for the project

1. Host a [Plausible](https://plausible.io/) (or compatible) instance you control.  
2. Edit `analytics.json` on `master`:

```json
{
  "enabled": true,
  "host": "https://YOUR-PLAUSIBLE/api/",
  "domain": "your.site.domain",
  "docs": "https://github.com/terenzif/GrindrPlus/blob/master/docs/analytics.md"
}
```

3. Users who want to contribute still enable **Opt-in analytics** in Settings.

Until then, leave `"enabled": false` so the whole fork stays quiet by default.

## Not the same as

- **Disable analytics** hook — that blocks *Grindr’s* Braze/Firebase/etc. inside the patched app.  
- GitHub Insights — only covers this repo’s clones/views, not manager installs on devices.

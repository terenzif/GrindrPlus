<p align="center">
  <img src="gplus_icon.svg" alt="GrindrPlus" width="150" height="150">
</p>

<h1 align="center">GrindrPlus</h1>

<p align="center">
  Fork mantenuto da <a href="https://github.com/terenzif">@terenzif</a> — modulo Xposed / LSPosed per Grindr.
</p>

<p align="center">
  <a href="https://github.com/terenzif/GrindrPlus/actions"><img src="https://img.shields.io/github/actions/workflow/status/terenzif/GrindrPlus/build_apk.yml?branch=master&logo=github&label=Build" alt="Build"></a>
  <a href="https://github.com/terenzif/GrindrPlus/releases"><img src="https://img.shields.io/github/v/release/terenzif/GrindrPlus?include_prereleases&label=Release" alt="Release"></a>
</p>

## Cos’è questo fork

Questo repository è il **fork attivo** di GrindrPlus dopo l’archiviazione del progetto upstream ([R0rt1z2/GrindrPlus](https://github.com/R0rt1z2/GrindrPlus)) e la fase PairIP / VM. Qui continuiamo supporto, mapping e rilasci per uso personale / lab.

**Direzione attuale (Fase 2):** target **Grindr 26.16.1** (`versionCode` 179451), con soft-fail per-hook e gate versione aggiornato. Baseline storica ancora documentata: **25.20.0**.

**In corso:** separazione **prodotto ↔ mapping** — pack JSON per `versionCode` sotto `app/src/main/assets/mappings/` e stub `MappingDictionary`, così un nuovo Grindr può richiedere un pack invece di un rebuild completo del core. I literal in `Obfuscation.kt` restano la fonte live finché la migrazione (Fase B) non è completa.

Il modulo **non** è affiliato a Grindr LLC. Usalo a tuo rischio.

## Disclaimer

Mod gratuito, senza garanzia. Non siamo responsabili di chat perse, ban, o altri problemi. Nessuna raccolta dati personali e nessuna pubblicità da parte di questo progetto — il codice è open source (GPL-3.0).

## Download

- Release di questo fork: [Releases](https://github.com/terenzif/GrindrPlus/releases)
- Build CI: [Actions](https://github.com/terenzif/GrindrPlus/actions)

Ogni build supporta **una** versione Grindr specifica (oggi **26.16.1**). Un client diverso può non avviare il modulo o degradare singoli hook.

## Installazione (LSPosed, consigliata)

**Requisiti:** root (Magisk / KernelSU) + [LSPosed](https://github.com/JingMatrix/LSPosed) funzionante (fork JingMatrix consigliato su Android recenti).

1. Installa l’APK del modulo da [Releases](https://github.com/terenzif/GrindrPlus/releases) (o CI).
2. Installa Grindr **26.16.1** (Play Store o bundle APKMirror + [SAI](https://github.com/Aefyr/SAI/releases)).
3. Abilita il modulo in LSPosed e metti Grindr nello scope.
4. Apri Grindr e verifica.

**Verifica rapida:** long-press sulla tab **Browse** → popup di stato GrindrPlus; cascade senza limite di profili e senza ads di terze parti.

> LSPatch / no-root esiste ancora nel codice manager, ma su questo fork il percorso **supportato e testato** è LSPosed. LSPatch porta limiti noti (login Google, mappe, stabilità).

## Funzionalità (ereditate / in manutenzione)

<details>
  <summary>Chat</summary>

  - Console comandi (`/help`)
  - Video call su chat nuove
  - Nascondi indicatori di chat
  - Elimina messaggi indipendentemente dall’età
</details>

<details>
  <summary>Media</summary>

  - Foto in scadenza illimitate
  - Visualizza tutti gli album ricevuti
  - Screenshot consentiti
</details>

<details>
  <summary>Global</summary>

  - Dettagli ban
  - Spoof Android ID
  - Analytics ridotti
  - Feature developer
  - Impostazioni mod / gestione hook
  - Disabilita update forzati
</details>

<details>
  <summary>Profiles / Location / Premium</summary>

  - BMI, boost indicator, copy profile ID, distanza, campi nascosti, online status, layout preferiti
  - Teleport / spoof location / location salvate
  - Cascade illimitata, Explore, filtri, no ads terze parti, frasi salvate, no boost upsell, hide views, incognito
</details>

Alcuni hook su 26.16.1 sono **skipped** o **partial** se il fingerprint DEX non c’è più — vedi soft-fail in `HookManager`.

## Bug noti (rilevanti)

- **Incognito:** instabile / si spegne da solo.
- **“Viewed Me”:** server-side, non modificabile.
- **Boost / Roaming:** disabilitati di default; riattiva disattivando l’hook “Disable Boosting”.
- **Crash / album:** prova a disabilitare “Unlimited Albums”.
- **Ad blocker:** profili vuoti → whitelist `cdn.cookielaw.org` o disabilita AdAway.

## Sviluppo

Vedi [docs/README.md](docs/README.md).

Mapping packs: `app/src/main/assets/mappings/<versionCode>.json`. Loader: `com.grindrplus.core.mapping.MappingDictionary` (stub — non ancora collegato a `init`).

## Crediti

- Idea e mod originale: [ElJaviLuki/GrindrPlus](https://github.com/ElJaviLuki/GrindrPlus)
- Riscrittura e manutenzione storica fino all’archivio: [R0rt1z2/GrindrPlus](https://github.com/R0rt1z2/GrindrPlus) e contributor
- LSPosed / LSPatch: [JingMatrix](https://github.com/JingMatrix)

## Licenza

GPL-3.0 — vedi [LICENSE](LICENSE). Non ricopiare il progetto spacciandolo per proprio: cita upstream e questo fork.

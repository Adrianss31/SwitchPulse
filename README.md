# SwitchPulse

Widget Android (Glance / Jetpack Compose) e app companion dedicata alle migliori offerte digitali **Nintendo eShop Italia** per **Nintendo Switch** e **Nintendo Switch 2**.

## Funzionalità principali

- **Editorial Bento Home Widget**: Hero card della migliore offerta #1 + lista Top 10 adattiva chiaro/scuro. Toccando un gioco si apre direttamente la scheda dettagliata in app (`GameDetailSheet`).
- **Feed & Ricerca globale**: Classifica Top 30 personalizzata + ricerca globale su tutto il catalogo idoneo in offerta, filtro piattaforma (`Tutte`, `Switch`, `Switch 2`), filtro `In scadenza (<48h)`, filtro sconto minimo e badge **Minimo** sui minimi storici rilevati localmente.
- **Libreria, Wishlist & Avvisi Prezzo**: Valuta 1–5★, aggiungi note per l'IA, segna i titoli posseduti o desiderati e imposta un prezzo target (`Avvisami sotto €`) per ricevere notifiche quando un gioco scende sotto soglia.
- **Multi-Provider AI (BYOK)**: Supporta **Google Gemini**, **OpenRouter** e **DeepSeek** con modelli configurabili per ciascun task (`Classifica AI`, `Metacritic & Verdetto`, `Anima del Giocatore`), analisi incrementale in cache locale SQLite, e selettore **Rerank AI in background** (`Solo Wi-Fi`, `Sempre`, `Mai`).
- **Anima del Giocatore & Scoperte**: Crea il tuo profilo ludico dalle valutazioni e dai commenti, genera consigli mirati fuori catalogo e nascondi permanentemente i suggerimenti scartati.
- **Backup & Ripristino**: Esporta e importa in JSON libreria, valutazioni, impostazioni, giochi nascosti e profilo Anima (le chiavi API cifrate con Android KeyStore non vengono mai esportate).
- **Auto-Update**: Controllo automatico delle release GitHub con verifica opzionale SHA-256 del file APK quando pubblicata dal workflow.

## Build & Firma Release

### Debug build e unit test

```bash
./gradlew testDebugUnitTest assembleDebug
```

### Firma Release

Per generare un APK o Bundle di release è necessario fornire il keystore `app/keystore/switchpulse-release.jks` e configurare le seguenti variabili d'ambiente o proprietà Gradle (senza credenziali di default nel repository):

- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

In CI (`.github/workflows/release.yml`), configurare i GitHub Secrets:

- `KEYSTORE_BASE64` (file `.jks` codificato in base64)
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

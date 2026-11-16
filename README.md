# SwitchPulse

Widget Android (Jetpack Glance) e app companion dedicata alle migliori offerte digitali **Nintendo eShop Italia** per **Nintendo Switch** e **Nintendo Switch 2**, con design editoriale (**A2 "Podio"**) ad alto contrasto.

## Sezioni principali

- **Classifica (`FeedScreen`)**:
  - Hero card #1 con ritaglio numero color carta e podio 2 colonne per #2 e #3, seguiti dalla classifica numerata fino alla posizione 30.
  - Ricerca globale istantanea su tutto il catalogo idoneo in offerta e filtro piattaforma (`Tutte`, `Switch 2`, `Switch`).
  - Foglio **Filtri** con ordinamento (`Per te`, `Sconto`, `Prezzo`), toggle `Solo giochi adatti a me` / `Solo in scadenza (< 48h)`, cursori con contatore dal vivo (`Mostra N offerte`) e gestione **Giochi nascosti**.
  - Gesti sulla riga: scorrimento a destra (**Ce l’ho**), scorrimento a sinistra (**Nascondi**) con barra *Annulla* da 4s, pressione lunga con popover 5★, transizione condivisa della copertina verso la **Scheda gioco** a tutto schermo.
- **Per te (`PerTeScreen`)**:
  - Ritratto AI **La tua anima da giocatore** con pilastri (*Cerchi* ed *Eviti*), modifica delle preferenze testuali (*Le tue parole*) e griglia 2 colonne **Da scoprire** per titoli fuori classifica.
- **Libreria (`LibraryScreen`)**:
  - Contatori (*ce l’ho*, *da giocare*, *votati*), barre dinamiche *Cosa guida i tuoi consigli*, schede filtro, ricerca di titoli dal catalogo Nintendo con *Aggiungi un gioco*, swipe-to-remove e soglie di avviso prezzo wishlist (`targetPriceEur`).
- **Impostazioni (`SettingsScreen`)**:
  - Raggiungibili dall’ingranaggio in Libreria: chiavi API cifrate con Android KeyStore (**Gemini**, **OpenRouter**, **DeepSeek**), modelli per singolo task, analisi AI in background (`Solo Wi-Fi` / `Sempre` / `Mai`), esportazione/importazione **Backup JSON** via SAF e **Auto-Update** da GitHub con verifica SHA-256.

## Widget Home Screen

1. **W1 · `Top 10 · Copertina`** (`TopCoverWidget`, 4×4 responsive): mostra in evidenza l’offerta #1 sulla copertina 16:9 completa con scadenza e sconto, e in basso le offerte #2–#4 pronte da aprire nella scheda in-app.
2. **W12 · `Top 10 · Carosello`** (`CarouselWidget`, 4×2): carosello interattivo a tre pannelli dell’intera Top 10 con immagine ad alto contrasto: tocca un pannello laterale per metterlo a fuoco al centro, oppure il pannello centrale grande per aprire il gioco in app.

## Build & Firma Release

### Debug build e unit test

```bash
./gradlew testDebugUnitTest assembleDebug
```

### Firma Release

Per generare un APK o Bundle di release è necessario fornire `app/keystore/switchpulse-release.jks` e configurare le variabili d’ambiente o proprietà Gradle (senza credenziali hardcoded nel repository):

- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

In CI (`.github/workflows/release.yml`), configurare i GitHub Secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

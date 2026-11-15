# SwitchPulse

App Android personale + widget **Editorial Bento** (Jetpack Glance, tema Chiaro/Scuro automatico) per monitorare ogni giorno le **Top 10 offerte popolari di Nintendo Switch e Switch 2** dall'eShop Italia.

## Funzionalità principali
- **Dati live senza server:** legge catalogo/popolarità da `search.nintendo-europe.com/it/select` e prezzi/scadenze reali in Euro da `api.ec.nintendo.com/v1/price`.
- **Filtri anti-rumore:** sconto minimo configurabile, prezzo di listino minimo e blacklist rapida per nascondere i titoli che non ti interessano.
- **Widget Home Android (Jetpack Glance):** #1 in evidenza con cover e badge, lista scorrevole #2–#10, switcher rapido `Tutti | SW2 | SW1` e refresh manuale.
- **Auto-Update da GitHub:** controlla l'ultima GitHub Release (`owner/repo`), scarica l'APK e lancia l'installer Android direttamente dall'app.

## Setup Auto-Update su GitHub
1. Crea un repository pubblico su GitHub e fai push del progetto su `main`.
2. Per permettere gli aggiornamenti in-place senza dover disinstallare l'app tra una release e l'altra, carica il keystore locale (`app/keystore/switchpulse-release.jks`) nei **GitHub Repository Secrets**:
   - `KEYSTORE_BASE64`: output di `base64 -i app/keystore/switchpulse-release.jks`
   - `KEYSTORE_PASSWORD`: `switchpulse123`
   - `KEY_ALIAS`: `switchpulse`
   - `KEY_PASSWORD`: `switchpulse123`
3. Ad ogni `git push` su `main`, la GitHub Action `.github/workflows/release.yml` compila, testa e pubblica una nuova Release (`v1.0.X`) con il file `.apk` allegato.
4. Nella tab **Filtri & Update** dell'app inserisci il tuo `owner/repo` (oppure viene già impostato automaticamente nelle build generate da GitHub Actions!).

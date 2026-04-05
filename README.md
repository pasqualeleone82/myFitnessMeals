# myFitnessMeals

Bootstrap Android Kotlin per MVP macro-tracking.

## Stack iniziale
- Kotlin + Android application module
- Jetpack Compose (Material 3)
- Coroutines
- Lifecycle + ViewModel
- CameraX + ML Kit barcode scanning
- Test setup (unit + instrumentation)

## Barcode logging flow
- Nella schermata Meal e disponibile il lookup barcode manuale e il pulsante Scan.
- Se il permesso camera viene negato, l'app mostra un messaggio non bloccante e mantiene il fallback manuale.
- Una scansione valida popola il campo barcode e avvia automaticamente il lookup cache-first/OFF.

## UI update (CR)
- La prima pagina dopo onboarding e la Dashboard.
- Le 4 tab principali sono nella bottom bar: Dashboard, Meal, History, Settings.
- Presente FAB centrale con menu rapido:
	- `Add food` -> apre Meal logging.
	- `Scan barcode` -> apre Meal logging e attiva scanner barcode.
- Alla pressione del back viene mostrata una conferma di uscita dall'app.
- Meal logging:
	- Unita porzione selezionabile via chip (`g`, `ml`, `serving`).
	- Quantita con input numerico decimale (supporto `.` e `,`).
	- Miglioramenti visuali con icone e card colorate.

## Internationalization (IT/EN)
- Introdotte risorse stringhe localizzate per inglese e italiano:
	- `app/src/main/res/values/strings.xml`
	- `app/src/main/res/values-it/strings.xml`
- Le schermate principali (Main navigation, Meal, Dashboard, History, Settings, dialog uscita) usano `stringResource(...)`.

## Garmin security notes
- Il collegamento Garmin richiede inserimento esplicito dell'authorization code in Settings.
- L'auth code viene validato prima dello scambio token.
- I token OAuth sono conservati solo in storage cifrato (`EncryptedSharedPreferences`) con comportamento fail-closed.
- Nessun fallback plaintext e supporto rotazione token su reconnect.
- In caso di token scaduto viene tentato refresh; se fallisce, lo stato passa a `REAUTH_REQUIRED`.

## Privacy data rights
- In Settings e presente la sezione Privacy con:
	- `Export data`: genera export JSON locale completo dei dati utente.
	- `Delete all data`: richiede conferma esplicita e cancella dati locali, connessioni provider, token OAuth e settings.

Percorso export locale (app-private storage):
- `files/exports/myfitnessmeals-export-<timestamp>.json`

## Requisiti locali
- JDK 17
- Android SDK con piattaforma API 35

## Comandi rapidi
```bash
./gradlew tasks
./gradlew lint
./gradlew test
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleDebug
```

## Stato test (ultima esecuzione)
- `./gradlew --no-daemon :app:connectedDebugAndroidTest` -> PASS (13/13)

## APK per device fisico
Build:
```bash
./gradlew --no-daemon :app:assembleDebug
```

APK generata:
- `app/build/outputs/apk/debug/app-debug.apk`

Install (USB debug attivo):
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## CI minima
Workflow GitHub Actions con esecuzione:
- `./gradlew tasks`
- `./gradlew lint`
- `./gradlew test`
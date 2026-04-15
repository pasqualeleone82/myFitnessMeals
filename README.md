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
- Presente FAB centrale (allineato otticamente al centro della bottom bar) con menu rapido:
	- `Add food` -> apre Meal logging.
	- `Scan barcode` -> apre Meal logging e attiva scanner barcode.
- Alla pressione del back viene mostrata una conferma di uscita dall'app.
- Meal logging:
	- Unita porzione selezionabile via chip (`g`, `ml`, `serving`).
	- Quantita con input numerico decimale (supporto `.` e `,`).
	- Selettore giorno operativo visibile (precedente/oggi/successivo) per scegliere esplicitamente la data del pasto.
	- Miglioramenti visuali con icone e card colorate.

## Onboarding profile + calorie estimate
- Durante l'onboarding, dopo l'inserimento dei dati profilo (eta, altezza, peso, sesso, livello attivita, obiettivo), viene visualizzato un blocco con il `Fabbisogno calorico stimato` (in kcal/giorno).
- Questo valore viene ricalcolato in tempo reale mentre l'utente modifica i dati profilo senza richiedere un'azione di refresh.
- Se i dati profilo sono incompleti, viene mostrato un placeholder ("Completa i dati profilo").
- Al completamento dell'onboarding, il target kcal visualizzato viene persistito per l'uso futuro.

## Settings profile editing + live calorie estimate
- In Settings il blocco profilo consente di modificare direttamente:
	- eta
	- peso
	- livello attivita
	- obiettivo (lose/maintain/gain)
- Il valore `Fabbisogno calorico stimato` viene ricalcolato in tempo reale mentre i valori profilo cambiano.
- Il pulsante `Save` persiste sia i valori profilo sia l'ultimo target kcal mostrato.
- Alla riapertura della schermata Settings i valori salvati vengono ricaricati e il target rimane coerente con il profilo persistito.
- I campi percentuale macro (carboidrati, proteine, grassi) mostrano sempre un singolo simbolo `%` senza rischio di duplicazione durante la digitazione, incolla, o cambio focus.

## FAB alignment correction
- Il FAB centrale (pulsante quick-add) è stato allineato otticamente al centro della bottom navigation bar across dispositivi (320dp, 360dp, 411dp) e orientamenti (portrait/landscape).
- Non ci sono sovrapposizioni con le etichette/icone della navigation bar.
- Il touch target rimane >= 48dp per motivi di accessibilita.

## Bug fix - meal save/history sync
- Root cause individuata: lo storico non veniva aggiornato sempre dopo il salvataggio pasti per stato tab stale.
- Fix applicato:
	- refresh di Dashboard/History al cambio tab.
	- salvataggio pasto legato alla data operativa selezionata in Meal logging.
	- snapshot meal/day caricato sulla data selezionata.

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

## CR verification - Profile estimate, FAB alignment, macro percent formatting
Per validare il comportamento delle tre fix:

**Unit tests (profilo/percentuale)**:
```bash
./gradlew :app:testDebugUnitTest --tests com.myfitnessmeals.app.ui.common.input.PercentFieldFormatterTest
./gradlew :app:testDebugUnitTest --tests com.myfitnessmeals.app.ui.onboarding.OnboardingViewModelTest
```

**UI tests (onboarding estimate, FAB alignment)**:
```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.myfitnessmeals.app.ui.onboarding.OnboardingEstimateUiTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.myfitnessmeals.app.main.MainFabAlignmentUiTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.myfitnessmeals.app.ui.settings.MacroPercentUiTest
```

**Manual validation**:
- Lanciare app in onboarding; inserire dati profilo e verificare che il valore "Fabbisogno calorico stimato" appare ed è aggiornato in tempo reale quando i valori cambiano.
- Completare onboarding e riaprire Settings; verificare che il target calorie sia coerente con i valori profilo salvati.
- In onboarding e settings, verificare che i campi percentuale macro non mostrino mai `%%` durante digitazione, incolla, o cambio focus.
- Lanciare su dispositivi fisici o emulatori a 320dp, 360dp, 411dp di larghezza e verificare che il FAB centrale sia allineato e non clippato.

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
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

## Requisiti locali
- JDK 17
- Android SDK con piattaforma API 35

## Comandi rapidi
```bash
./gradlew tasks
./gradlew lint
./gradlew test
```

## CI minima
Workflow GitHub Actions con esecuzione:
- `./gradlew tasks`
- `./gradlew lint`
- `./gradlew test`
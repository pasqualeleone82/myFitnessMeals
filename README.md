# myFitnessMeals

Bootstrap Android Kotlin per MVP macro-tracking.

## Stack iniziale
- Kotlin + Android application module
- Jetpack Compose (Material 3)
- Coroutines
- Lifecycle + ViewModel
- Test setup (unit + instrumentation)

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
# Architecture - myFitnessMeals (Android MVP)

## Overview
myFitnessMeals e un'app Android phone-first per macro tracking con priorita alla velocita di logging e resilienza offline. L'architettura proposta adotta un modello cache-first con sorgente autorevole locale, fallback remoto su Open Food Facts (OFF), correzioni utente persistenti sui nutrienti e integrazione Garmin MVP per metriche fitness giornaliere. Le decisioni privilegiano time-to-market, affidabilita offline e separazione netta dei moduli (presentation/domain/data/integration), mantenendo estendibilita verso futuri provider (Fitbit/Google Fit).

## 1) ADR sintetici (decisioni architetturali)

### ADR-S01 - Source of Truth locale (Room) con strategia cache-first
- Decisione: i dati applicativi core (alimenti cache, diario, override, fitness giornaliero, impostazioni) risiedono in DB locale Room come fonte primaria di lettura/scrittura.
- Trade-off: maggiore complessita di sincronizzazione e migrazioni in cambio di UX offline affidabile e latenze basse.
- Alternative scartate:
  - Solo rete con cache volatile in-memory: piu semplice ma non compatibile con RNF offline.
  - Backend proprietario obbligatorio per tutte le query: aumenta costi e dipendenze, non richiesto MVP.

### ADR-S02 - Architettura modulare Clean-ish con separazione presentation/domain/data/integration
- Decisione: separare use case e regole business dal layer dati e dalle integrazioni.
- Trade-off: piu boilerplate iniziale, ma testabilita e manutenibilita superiori.
- Alternative scartate:
  - Monolite per feature senza domain layer: piu veloce all'inizio, ma fragile su evoluzioni e test.

### ADR-S03 - Jetpack Compose + ViewModel + StateFlow
- Decisione: UI dichiarativa Compose con unidirectional data flow.
- Trade-off: learning curve su team non allineato, compensata da sviluppo rapido UI e stato prevedibile.
- Alternative scartate:
  - XML + Fragment classici: maggiore verbosita e rischio inconsistenze di stato.

### ADR-S04 - Nutrient resolution policy deterministica: Override > Cache > OFF
- Decisione: policy unica in domain service per garantire coerenza in ogni flusso.
- Trade-off: necessita metadati di provenienza e merge esplicito; beneficio: prevedibilita e trasparenza.
- Alternative scartate:
  - Merge "best effort" campo-per-campo da sorgenti multiple: meno deterministico e difficile da spiegare in UI.

### ADR-S05 - Normalizzazione nutrienti su base 100g/100ml
- Decisione: persistere nutrienti normalizzati per semplificare calcoli porzione.
- Trade-off: conversioni iniziali complesse; beneficio: calcoli lineari e verificabili.
- Alternative scartate:
  - Salvataggio payload raw OFF: rischio unita incoerenti e bug di calcolo.

### ADR-S06 - Sync Garmin asincrona con WorkManager (periodica + on-demand)
- Decisione: sync Garmin triggerata all'apertura app (app-open) e on-demand, con retry esponenziale su errori transienti.
- Trade-off: minore freschezza rispetto a schedulazione periodica continua, ma minore consumo batteria e complessita MVP.
- Alternative scartate:
  - Sync periodica fissa (es. ogni 6h): maggiore complessita operativa per MVP.

### ADR-S07 - OAuth 2.0 con token in Android Keystore/Encrypted storage
- Decisione: conservare access/refresh token cifrati e referenziati tramite repository sicuro.
- Trade-off: implementazione security piu articolata, ma compliance RNF sicurezza.
- Alternative scartate:
  - SharedPreferences in chiaro: non accettabile per sicurezza.

### ADR-S08 - Repository pattern con policy di fetch esplicite
- Decisione: repository espongono contratti cache-first, remote-first (solo dove serve) e local-only.
- Trade-off: piu superficie API interna, ma comportamento prevedibile e testabile.
- Alternative scartate:
  - Policy implicita distribuita nei ViewModel: rischio regressioni e duplicazioni.

### ADR-S09 - Telemetria tecnica minimizzata e privacy-by-design
- Decisione: tracciare solo eventi tecnici (funnel e errori) con payload non sensibile.
- Trade-off: analisi prodotto meno granulare; beneficio: riduzione rischio privacy/compliance.
- Alternative scartate:
  - Eventi dettagliati con alimenti/biometria raw: non coerente con minimizzazione dati.

### ADR-S10 - API esterne isolate dietro adapter e anti-corruption layer
- Decisione: mapping OFF/Garmin in DTO interni stabili per proteggere il dominio.
- Trade-off: codice mapping extra; beneficio: minore impatto da cambi API terze parti.
- Alternative scartate:
  - Uso diretto modelli SDK/API nei use case: accoppiamento e fragilita elevata.

### ADR-S11 - Aggregati giornalieri materializzati e aggiornati atomicamente
- Decisione: mantenere tabella daily_summary aggiornata su write path (insert/update/delete meal entry).
- Trade-off: logica transazionale aggiuntiva; beneficio: dashboard veloce e stabile.
- Alternative scartate:
  - Calcolo aggregati on-the-fly ad ogni apertura dashboard: costo CPU e latenza maggiore.

### ADR-S13 - Profilo obiettivo utente selezionabile nel modello singolo profilo
- Decisione: mantenere installazione single-user ma con target mode selezionabile (perdita, mantenimento, aumento).
- Trade-off: no multi-account in MVP, ma maggiore personalizzazione obiettivo.
- Alternative scartate:
  - Multi-profilo completo in MVP: costo elevato e rischio scope creep.

### ADR-S14 - Data rights MVP (export + delete)
- Decisione: includere in MVP export dati utente e cancellazione completa dati locali con conferma esplicita.
- Trade-off: aumento effort su privacy tooling, beneficio compliance e fiducia utente.
- Alternative scartate:
  - Posticipare a post-MVP: rischio debito compliance.

### ADR-S12 - Timezone locale snapshot per coerenza giornaliera
- Decisione: ogni evento giornaliero salva local_date e timezone_offset al momento del logging.
- Trade-off: modello dati leggermente piu ricco; beneficio: robustezza su cambio fuso (EC-010).
- Alternative scartate:
  - Derivare sempre da timestamp UTC corrente: risultati incoerenti in giorni di cambio timezone.

## 2) Architettura logica a moduli (presentation/domain/data/integration)

### Presentation
- Responsabilita:
  - Schermate: Onboarding, Diario/Pasti, Ricerca testo, Scanner barcode, Dettaglio alimento, Dashboard, Storico, Impostazioni, Connessione provider, Privacy dati.
  - Gestione stato UI con ViewModel + StateFlow.
  - Validazioni di input UX-level (es. quantita > 0, macro totale 100 come feedback immediato).
  - Card macro con doppia rappresentazione (grammi + percentuali) e stato nutrienti mancanti discreto.
- Contratti in ingresso dal domain:
  - ObserveDashboardUseCase(date)
  - SearchFoodUseCase(query, mealType)
  - LookupBarcodeUseCase(barcode)
  - SaveMealEntryUseCase(command)
  - UpdateNutritionOverrideUseCase(command)
  - SyncGarminNowUseCase()

### Domain
- Responsabilita:
  - Regole business RB-001..RB-012.
  - Calcoli nutrizionali, risoluzione priorita sorgenti, calcolo calorie rimanenti.
  - Calcolo target kcal default con Mifflin-St Jeor + fattore attività + delta obiettivo settimanale.
  - Validazione forte (macro=100, quantita positiva, coerenza unita).
- Componenti principali:
  - NutrientResolverService
  - MacroCalculatorService
  - DailySummaryService
  - GoalComputationService
  - FitnessMergeService
- Eventi dominio (interni):
  - MealEntryAdded, MealEntryDeleted, NutritionOverrideUpdated, FitnessSynced, SyncFailed.

### Data
- Responsabilita:
  - Persistenza Room, DAO, transazioni.
  - Repository e policy fetch.
  - Caching metadati (TTL, freshness, source).
- Contratti repository:
  - FoodRepository: ricerca testo/barcode, upsert cache, source provenance.
  - MealRepository: CRUD voci, ricalcolo aggregati atomico.
  - OverrideRepository: CRUD override nutrienti.
  - GoalRepository: profilo/target/macro.
  - FitnessRepository: dati giornalieri e stato sync.
  - ProviderConnectionRepository: connessioni OAuth e metadati token.

### Integration
- Responsabilita:
  - Client OFF (HTTP), client Garmin (OAuth + API metrics), adapter analytics/crash.
  - WorkManager jobs per sync pianificata.
- Contratti adapter:
  - OffCatalogAdapter.searchByText(query)
  - OffCatalogAdapter.searchByBarcode(code)
  - GarminAuthAdapter.connect()/disconnect()/refreshToken()
  - GarminFitnessAdapter.fetchDailyMetrics(dateRange)

### Data flow end-to-end (sintesi)
- Ricerca testo/barcode:
  - UI -> UseCase -> FoodRepository.cacheSearch.
  - Cache hit: ritorno immediato con source=LOCAL/OVERRIDE.
  - Cache miss: chiamata OFF adapter, mapping/normalizzazione, persistenza cache, ritorno risultato.
- Salvataggio pasto:
  - UI -> SaveMealEntryUseCase -> NutrientResolver -> MealRepository.transaction(save + recompute daily_summary).
- Sync Garmin:
  - Trigger manuale o periodico -> WorkManager -> Garmin adapter -> upsert fitness_daily -> aggiornamento sync_status.

## 3) Scelta stack Android e librerie consigliate
- Linguaggio: Kotlin.
- UI: Jetpack Compose, Material 3.
- Architettura: AndroidX ViewModel, Lifecycle, Navigation Compose.
- Concorrenza: Kotlin Coroutines + Flow/StateFlow.
- DI: Hilt.
- Persistenza locale: Room + SQLite.
- Networking: Retrofit + OkHttp + Kotlinx Serialization (o Moshi).
- Job scheduling: WorkManager.
- Sicurezza storage: Android Keystore + EncryptedSharedPreferences (token envelope).
- Barcode scanning: ML Kit Barcode Scanning (o CameraX + ZXing fallback).
- Logging/analytics tecnico:
  - Crash reporting: Firebase Crashlytics (o Sentry).
  - Event analytics tecnico: Firebase Analytics (con schema eventi minimizzato) o PostHog self-hosted.
- Test:
  - Unit: JUnit5, Kotest (opzionale), Turbine per Flow.
  - UI: Compose UI Test.
  - Integration: Room in-memory + MockWebServer.

Trade-off principali stack:
- Hilt vs Koin: Hilt scelto per integrazione Android enterprise-grade, a costo di compilazione piu pesante.
- Retrofit vs Ktor client: Retrofit piu standard Android, Ktor piu flessibile cross-platform ma non prioritario MVP.
- Crashlytics vs Sentry: Crashlytics semplice per team Android; Sentry piu potente su tracing.

## 4) Modello dati locale (tabelle principali e relazioni)

### Tabelle principali
- user_profile
  - id (PK), weight_current_kg, weight_goal_kg, weekly_goal_kg, activity_level, target_kcal, macro_carb_pct, macro_fat_pct, macro_protein_pct, updated_at.
- food_item
  - id (PK), source_id, source (LOCAL/OFF), name, brand, barcode, portion_ref_value, portion_ref_unit, kcal_100, carb_100, fat_100, protein_100, sugars_100, sodium_100, micros_json, completeness_score, last_synced_at.
- food_alias
  - id (PK), food_id (FK->food_item), query_token, indice full-text/like support.
- nutrition_override
  - id (PK), food_id (FK->food_item), kcal_100, carb_100, fat_100, protein_100, micros_json, note, created_at, updated_at.
- meal_entry
  - id (PK), local_date, timezone_offset_min, meal_type (breakfast/lunch/dinner/snack), food_id (FK->food_item), quantity_value, quantity_unit, resolved_source (OVERRIDE/CACHE/OFF), kcal_total, carb_total, fat_total, protein_total, created_at, updated_at.
- daily_summary
  - local_date (PK), kcal_target, kcal_intake, kcal_burned, kcal_remaining, carb_total, fat_total, protein_total, updated_at.
- weight_measurement
  - id (PK), local_date, weight_kg, source (manual/import), created_at.
- fitness_daily
  - local_date (PK), provider (GARMIN), steps, active_kcal, workout_minutes, last_sync_at, sync_status.
- provider_connection
  - provider (PK), connection_state, token_ref, scopes, last_sync_at, last_error_code, updated_at.
- sync_checkpoint
  - provider + entity (PK composita), cursor, updated_at.

### Relazioni
- food_item 1:N nutrition_override.
- food_item 1:N meal_entry.
- daily_summary 1:1 con local_date; aggrega meal_entry + fitness_daily.
- provider_connection 1:N logical con fitness_daily (per provider/date).

## 5) Strategia cache-first e sincronizzazione

### Cache-first per catalogo alimenti
- Step 1: query locale (FTS/token index + barcode index).
- Step 2: se hit, ritorno immediato e render UI.
- Step 3: se miss, chiamata OFF con timeout breve e retry controllato.
- Step 4: mapping/normalizzazione nutrienti, deduplica per barcode + similarita nome/brand.
- Step 5: persistenza locale con metadati last_synced_at, completeness_score, source.

### Coerenza e invalidazione cache
- TTL soft (es. 30 giorni) per record OFF, refresh lazy in background quando stale.
- Never-delete aggressivo in MVP; usare LRU/trim graduale sopra soglia (es. 20k alimenti, RNF-013).
- Override utente non viene invalidato da refresh OFF.

### Sync dati fitness
- Tipi sync: automatica all'apertura app + manuale da UI.
- Retry policy: exponential backoff, max attempts finite, stato errore persistito.
- Idempotenza: upsert per local_date/provider.
- Conflitti: fitness non sovrascrive mai voci alimentari; aggiorna solo fitness_daily e daily_summary.kcal_burned.

## 6) Design integrazione Open Food Facts

### Contratto logico OFF adapter
- Operazioni:
  - searchFoods(query, page, pageSize)
  - getFoodByBarcode(barcode)
- Input sanitization:
  - trim query, blocco stringhe vuote, normalizzazione barcode EAN/UPC.
- Output canonico interno:
  - ExternalFoodRecord { externalId, name, brand, barcode, nutrientsPer100, servings, sourceMeta }.

### Mapping e normalizzazione
- Mappare nutrienti noti (kcal/carbo/fat/protein/sugars/sodium).
- Convertire unita in grammi/ml standard.
- Campi mancanti -> null e visualizzazione N/D.
- Calcolare completeness_score per deduplica e scelta record migliore (RB-012).

### Resilienza
- Timeout client (es. 2-3 s), cooldown locale in caso di failure ripetute.
- Error taxonomy interna:
  - OffTimeout, OffRateLimited, OffUnavailable, OffMalformedPayload, OffNotFound.
- UX fallback:
  - errore non bloccante + Retry (AC-022) + inserimento manuale barcode/nutrienti base.

### Trade-off e alternative
- API OFF diretta vs backend proxy:
  - Scelta MVP: diretta in app per ridurre time-to-market.
  - Scartata (per ora) proxy backend: migliore controllo rate limit/monitoring ma maggiore costo infrastruttura.

## 7) Design integrazione Garmin Connect (OAuth, token, sync jobs)

### OAuth 2.0 flow (MVP)
- Avvio da impostazioni -> browser/custom tab verso authorization endpoint Garmin.
- Redirect URI verso app -> scambio code->token tramite endpoint token.
- Persistenza token:
  - access/refresh token cifrati in storage sicuro.
  - in DB salvare solo token_ref e metadata (scope, scadenza, stato).

### Gestione token
- Refresh automatico prima di chiamate sync se token in scadenza.
- In caso refresh failure:
  - connection_state=reauth_required.
  - CTA di riconnessione in UI.
- Disconnect:
  - wipe token sicuro + stato disconnesso.

### Sync jobs
- GarminSyncWorker (WorkManager):
  - Trigger: app-open + on-demand.
  - Steps:
    - validare connessione e token
    - fetch range date (ultimo checkpoint -> oggi)
    - mappare steps/active_kcal/workout_minutes
    - upsert fitness_daily
    - aggiornare daily_summary e sync_checkpoint
- Error handling:
  - transient error -> retry backoff.
  - auth error -> stop retry, stato reauth_required.

### Contratti interfaccia integrazione
- connectProvider(provider: GARMIN): ConnectionResult
- disconnectProvider(provider: GARMIN): DisconnectResult
- syncFitness(provider: GARMIN, mode: MANUAL|PERIODIC): SyncResult
- observeProviderStatus(provider: GARMIN): Flow<ProviderStatus>

## 8) Sicurezza/privacy (threats + mitigazioni)

### Threat model sintetico
- T1: esfiltrazione token OAuth dal dispositivo.
- T2: intercettazione traffico rete su API esterne.
- T3: leakage di dati sensibili nei log.
- T4: manipolazione locale DB su device compromesso.
- T5: abuso endpoint esterni (rate limit/DoS logico).

### Mitigazioni
- M1 (T1): Keystore + cifratura token, zero token in plain text (AC-018).
- M2 (T2): TLS obbligatorio, certificate pinning valutato come hardening post-MVP.
- M3 (T3): redaction di barcode/token/PII, no payload nutrizionale dettagliato in analytics.
- M4 (T4): validazioni dominio, checksum opzionale su record critici; limite noto su device rooted.
- M5 (T5): timeout + retry limitati + cooldown locale su errori ripetuti.

## 9) Prestazioni e osservabilita

### Obiettivi prestazionali (RNF)
- Ricerca cache <= 300 ms p95.
- Apertura dettaglio locale <= 1 s p95.
- Salvataggio voce pasto <= 400 ms p95.

### Strategie performance
- Indici DB su barcode/query/date.
- Query incremental e paginazione risultati ricerca.
- Aggiornamento daily_summary in transazione atomica evitando query aggregate costose ripetute.
- Precompute valori derivati usati in dashboard.

### Osservabilita
- Logging strutturato con codici errore (origin, operation, reason).
- Eventi tecnici minimi:
  - food_search_executed
  - meal_entry_saved
  - provider_sync_executed
  - provider_sync_failed
- Metriche operative:
  - cache hit ratio
  - sync success rate
  - retry count
  - crash-free sessions

## 10) Piano test tecnico

### Test unitari (domain)
- Calcolo macro/calorie con tolleranza <=1%.
- Risoluzione priorita fonti Override > Cache > OFF.
- Validazioni macro=100, quantita positiva.
- Regole aggregazione daily_summary e formula calorie rimanenti.

### Test integrazione (data/integration)
- Room DAO + transaction boundaries su insert/delete meal e ricalcolo aggregati.
- OFF adapter con MockWebServer:
  - success, timeout, payload incompleto, non trovato, rate limit.
- Garmin adapter:
  - OAuth success/failure/refresh expired.
  - sync idempotente per stessa data.

### Test UI/strumentali
- Flussi AC-001..AC-017 critici (onboarding, ricerca, logging, override, dashboard, offline).
- Accessibilita base con font scale 200%.
- Barcode flow: scanner + fallback manuale.

### Test non funzionali
- Benchmark locali su query/search/save (p95).
- Soak test sync periodica.
- Security review su persistenza token e log redaction.

## 11) Piano migrazioni/evoluzione (Fitbit/Google Fit)

### Principio architetturale
- Definire provider abstraction stabile:
  - FitnessProviderAuthPort
  - FitnessProviderSyncPort
  - FitnessProviderMapper

### Evoluzione fase 2
- Aggiunta provider Fitbit/Google Fit come nuovi adapter senza modificare use case core.
- Estensione tabella provider_connection multi-provider.
- fitness_daily mantiene chiave (local_date, provider) e vista composita per dashboard.

### Migrazioni DB previste
- V2: aggiunta campi provider-specific metadata (es. source_granularity).
- V3: supporto multi-account opzionale (se futuro multi-profilo).
- Migrazioni backward-safe con script Room Migration e fallback export/import locale per casi critici.

### Alternative scartate
- Integrare Fitbit/Google Fit gia in MVP: rischio ritardi elevato su OAuth e QA cross-provider.

## 12) Rischi tecnici e fallback

- Rischio: qualita variabile OFF (dati incompleti/incoerenti).
  - Fallback: UI N/D + override utente + scoring completezza + deduplica.
- Rischio: limitazioni o cambi API Garmin.
  - Fallback: isolamento adapter, feature flag per disabilitare sync mantenendo core funzionante.
- Rischio: crescita cache locale oltre soglia.
  - Fallback: strategia LRU soft, vacuum/maintenance periodica.
- Rischio: regressioni su timezone/date.
  - Fallback: snapshot timezone_offset e test specifici EC-010.
- Rischio: performance su device low-end.
  - Fallback: query ottimizzate, riduzione payload UI, lazy rendering Compose.
- Rischio: failure rete persistente.
  - Fallback: modalita offline completa per funzioni locali e retry esplicito utente.

## 13) Decisioni di prodotto recepite (2026-03-28)
- Profilo obiettivo selezionabile in onboarding/settings (perdita/mantenimento/aumento).
- Visualizzazione macro in grammi e percentuali entrambe in dashboard.
- Nutrienti mancanti: comunicazione discreta, non bloccante.
- Override nutrienti: mantenimento ultimo valore, no versioning storico in MVP.
- Sync Garmin: automatica su apertura app + manuale; no scheduler periodico in MVP.
- Errori sync Garmin: notifica temporanea (toast/snackbar), non banner persistente.
- Privacy MVP: export dati e cancellazione completa dati locali inclusi.

## Definition of Ready per Coder - Checklist
- [ ] Contratti use case/domain confermati (input/output/errori tipizzati).
- [ ] Schema Room e migrazioni V1 approvati (entita, indici, vincoli).
- [ ] Policy cache-first e priorita sorgenti formalizzate in test spec.
- [ ] Contratti OFF/Garmin adapter congelati con mapping canonico.
- [ ] Strategia token security (Keystore + encrypted storage) validata.
- [ ] Piano WorkManager (periodic + on-demand + retry) approvato.
- [ ] Event taxonomy osservabilita definita e minimizzata.
- [ ] Criteri prestazionali p95 strumentabili in build debug.
- [ ] Casi edge EC-001..EC-012 coperti in test plan tecnico.
- [ ] Feature flag MVP e fallback operativi documentati.

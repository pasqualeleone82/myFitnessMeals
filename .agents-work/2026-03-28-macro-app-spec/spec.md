# myFitnessMeals - Specifica Funzionale MVP Android

## Problema
Chi desidera perdere peso, mantenerlo o ottimizzare la composizione corporea fatica a registrare i pasti con costanza perche l'inserimento e lento, i dati nutrizionali sono spesso incompleti e manca un quadro unico tra intake alimentare e attivita fisica.

## Obiettivi
- Ridurre il tempo medio di logging di un alimento rispetto a inserimento manuale puro.
- Consentire monitoraggio giornaliero affidabile di calorie e macronutrienti.
- Fornire una dashboard leggibile con stato obiettivi, attivita e trend peso.
- Abilitare personalizzazione obiettivi calorici e macro basata su profilo utente.
- Integrare Garmin Connect come fonte iniziale di metriche attivita.
- Gestire dati incompleti con correzioni utente persistenti e riutilizzabili.

## Scope MVP
- App Android phone-first, profilo singolo per installazione con scelta del profilo obiettivo (perdita, mantenimento, aumento peso).
- Diario alimentare con pasti: colazione, pranzo, cena, spuntini.
- Ricerca alimento testuale e scansione barcode.
- Strategia ricerca cache-first con fallback Open Food Facts.
- Dettaglio alimento con porzioni/unita e calcolo dinamico nutrienti.
- Correzione manuale nutrienti e priorita override utente.
- Dashboard giornaliera con card calorie, card macro e widget fitness base.
- Impostazioni obiettivi: peso, target settimanale, livello attivita, macro, kcal.
- Integrazione Garmin Connect con sync passi, calorie attive, durata allenamenti.
- Storico giornaliero di intake, macro, peso e metriche fitness principali.
- Export dati utente e cancellazione completa dati locali (diritto all'oblio) in MVP.

## Out of Scope
- Versione iOS, web o tablet ottimizzata.
- Coaching automatico, piano pasti intelligente o suggerimenti AI.
- Funzionalita social (community, leaderboard, condivisione pasti).
- Integrazioni Fitbit e Google Fit nel rilascio MVP.
- Multi-profilo nello stesso dispositivo.
- Funzionalita cliniche/mediche (diagnosi, prescrizioni, certificazioni).
- Pagamenti, abbonamenti o funzionalita premium.

## Flussi Utente
1. Onboarding iniziale
- L'utente inserisce dati antropometrici e obiettivo.
- Il sistema propone target kcal/macro modificabili prima del salvataggio.

2. Logging alimento via testo
- L'utente seleziona un pasto.
- Cerca un alimento con testo libero.
- Il sistema interroga cache locale, poi OFF se non presente.
- L'utente seleziona porzione e quantita e conferma.

3. Logging alimento via barcode
- L'utente apre scanner.
- Il sistema legge codice e cerca cache-first.
- In assenza, interroga OFF e mostra dettaglio alimento.

4. Correzione valori nutrizionali
- L'utente apre dettaglio alimento.
- Modifica nutrienti mancanti/errati.
- Il sistema salva override locale con timestamp.

5. Consultazione dashboard
- L'utente vede calorie obiettivo/assunte/bruciate/rimanenti.
- Consulta stato macro e widget passi, peso, calorie esercizio, tempo allenamento.

6. Connessione integrazione Garmin
- L'utente autorizza Garmin da impostazioni.
- Il sistema salva token in storage sicuro e sincronizza dati.

7. Revisione storico
- L'utente naviga giorni precedenti e confronta trend peso/intake/attivita.

## User Stories
- Come utente voglio aggiungere cibi rapidamente per non abbandonare il tracking.
- Come utente voglio usare il barcode per ridurre errori di inserimento.
- Come utente voglio capire subito l'impatto di una porzione sui macro.
- Come utente voglio modificare nutrienti incompleti cosi da rendere il diario accurato.
- Come utente voglio vedere quante calorie mi restano oggi.
- Come utente voglio impostare il mio obiettivo peso con ritmo settimanale.
- Come utente voglio integrare Garmin per evitare doppio inserimento attivita.
- Come utente voglio vedere lo storico per capire se sto progredendo.

## Requisiti Funzionali
- RF-001: Creazione, modifica ed eliminazione voci nei pasti colazione, pranzo, cena, spuntini.
- RF-002: Ricerca testuale con suggerimenti e ordinamento per rilevanza.
- RF-003: Scansione barcode EAN/UPC con fallback inserimento manuale codice.
- RF-004: Lookup cache-first obbligatorio per testo e barcode.
- RF-005: Fallback automatico a OFF se cache locale non contiene risultato valido.
- RF-006: Dettaglio alimento con selezione unita (g, ml, porzione API) e quantita personalizzabile.
- RF-007: Aggiornamento in tempo reale di calorie e macro al variare quantita/unita.
- RF-008: Visualizzazione nutrienti dettagliati per categorie (macro, grassi, zuccheri, sodio, micronutrienti).
- RF-009: Gestione valori mancanti con etichetta N/D senza blocco flusso.
- RF-010: Modifica manuale nutrienti da parte utente con salvataggio override persistente.
- RF-011: Priorita lettura nutrienti: override utente, poi cache, poi OFF.
- RF-012: Possibilita di aggiungere nota utente all'override (es. fonte etichetta).
- RF-013: Calcolo target kcal iniziale da profilo, livello attivita e obiettivo settimanale.
- RF-014: Configurazione manuale target kcal e percentuali macro da impostazioni.
- RF-015: Validazione somma percentuali macro uguale a 100 prima del salvataggio.
- RF-016: Dashboard con card calorie (obiettivo, assunte, bruciate, rimanenti).
- RF-017: Dashboard con card macro separata in tre indicatori (carbo, grassi, proteine).
- RF-017A: Card macro con doppia vista obbligatoria: grammi e percentuali (toggle o doppia etichetta simultanea).
- RF-018: Widget dashboard per passi giornalieri.
- RF-019: Widget dashboard per calorie esercizio giornaliere.
- RF-020: Widget dashboard per tempo totale allenamento giornaliero.
- RF-021: Widget dashboard per trend peso con ultimo valore disponibile.
- RF-022: Connessione/disconnessione Garmin da impostazioni.
- RF-023: Sincronizzazione Garmin on-demand e periodica con stato ultimo sync.
- RF-023A: Sincronizzazione Garmin automatica all'apertura app (app-open sync) con possibilità manuale da impostazioni.
- RF-023B: In caso di errore sync Garmin, mostrare notifica temporanea non bloccante con dettaglio sintetico errore.
- RF-024: Storico giornaliero consultabile per almeno 90 giorni in MVP.
- RF-025: Gestione errori di rete con messaggio non bloccante e retry esplicito.
- RF-026: Modalita offline per consultazione dati locali e logging alimenti da cache.
- RF-027: Tracciamento eventi principali (ricerca, aggiunta alimento, sync, errore).
- RF-028: Possibilita di eliminare una voce pasto e aggiornare aggregati in tempo reale.
- RF-029: Visualizzazione origine dato alimento (override, cache, OFF) nel dettaglio tecnico.
- RF-030: Reset locale cache alimento da schermata debug utente avanzato (opzionale visibile via flag).
- RF-031: Pulsante rapido "+" (nice to have) in home/dashboard per avviare inserimento alimento.
- RF-032: Export dati utente in formato leggibile (es. JSON/CSV) da impostazioni privacy.
- RF-033: Cancellazione completa dati utente locali da impostazioni privacy con conferma esplicita.

## Requisiti Non Funzionali
- RNF-001 (Performance): ricerca cache locale <= 300 ms p95.
- RNF-002 (Performance): transizione a dettaglio alimento <= 1 s p95 con dato locale.
- RNF-003 (Performance): salvataggio voce pasto <= 400 ms p95.
- RNF-004 (Affidabilita): crash-free sessions >= 99.5% su build stable.
- RNF-005 (Affidabilita): modalita offline disponibile senza blocchi per funzioni locali.
- RNF-006 (Accuratezza): scostamento calcolo macro/calorie <= 1% rispetto formula standard interna.
- RNF-007 (Sicurezza): token OAuth cifrati in secure storage Android.
- RNF-008 (Privacy): minimizzazione dati personali e assenza di dati sanitari non necessari.
- RNF-009 (Usabilita): aggiunta alimento completa in <= 4 tap mediani esclusa digitazione.
- RNF-010 (Compatibilita): supporto Android API 26+.
- RNF-011 (Osservabilita): logging errori con codice, origine e contesto minimo riproducibile.
- RNF-012 (Manutenibilita): separazione modulo dominio calcoli nutrizionali con test unitari dedicati.
- RNF-013 (Scalabilita dati): gestione locale di almeno 20k alimenti cache senza degrado evidente UX.
- RNF-014 (Accessibilita): supporto font scaling Android fino a 200% senza perdita funzionale.

## Integrazioni Esterne
1. Open Food Facts API
- Uso: ricerca testo, ricerca barcode, recupero nutrienti/porzioni.
- Vincoli: qualita campi eterogenea, timeout, rate limit.
- Strategia: cache locale, mapping unita, normalizzazione nutrienti per 100 g/ml.

2. Garmin Connect
- Uso: import passi, calorie attive, durata allenamenti.
- Accesso: OAuth 2.0 con consenso utente.
- Sicurezza: token in secure storage, revoca da impostazioni.

3. Analytics tecnico (strumento da definire in architettura)
- Uso: eventi funnel onboarding, ricerca, logging, sync.
- Vincoli: no invio dati sensibili in chiaro.

## Modello Dati Concettuale
- UtenteProfilo
  - id, sesso (opzionale), eta (opzionale), altezza (opzionale), pesoAttuale, pesoObiettivo, obiettivoSettimanaleKg, livelloAttivita, targetKcal, targetMacroPerc, allenamentiSettimanali.
- Alimento
  - idLocale, idFonte, fonte, nome, brand, barcode, porzioni, nutrientiPer100, ultimoAggiornamento.
- OverrideAlimentoUtente
  - idOverride, idAlimento, nutrientiOverride, nota, timestampCreazione, timestampAggiornamento.
- VocePasto
  - idVoce, dataLocale, tipoPasto, idAlimento, quantita, unita, nutrientiCalcolati, calorieCalcolate, fonteDato.
- DiarioGiornaliero
  - dataLocale, totaleCalorieAssunte, totaleMacroAssunti, calorieBruciate, calorieRimanenti.
- MisuraPeso
  - idMisura, dataLocale, pesoKg, fonteInserimento.
- AttivitaFitnessGiornaliera
  - dataLocale, passi, calorieAttive, minutiAllenamento, provider, ultimoSync.
- ConnessioneProvider
  - provider, statoConnessione, tokenRef, scopeConcessi, ultimoSync, statoErrore.

Relazioni concettuali:
- UtenteProfilo 1:N VocePasto.
- VocePasto N:1 Alimento.
- Alimento 1:N OverrideAlimentoUtente.
- UtenteProfilo 1:N MisuraPeso.
- UtenteProfilo 1:N AttivitaFitnessGiornaliera.
- UtenteProfilo 1:N DiarioGiornaliero.

## Regole Business
- RB-001: Priorita sorgente alimento: Override > Cache locale > OFF.
- RB-002: Nutrienti voce pasto scalano linearmente rispetto alla porzione riferimento.
- RB-003: Calorie rimanenti = targetKcal - calorieAssunte + calorieBruciate.
- RB-004: Somma percentuali macro deve essere 100 per salvare impostazioni.
- RB-005: Se un nutriente e assente, mostrare N/D e consentire override.
- RB-006: Ogni override deve salvare timestamp e utente proprietario.
- RB-007: Sync fitness non puo alterare voci alimentari manuali.
- RB-008: Ricalcolo target kcal quando cambiano peso attuale, obiettivo o attivita.
- RB-009: In assenza rete, nessuna chiamata remota; usare solo dati locali.
- RB-010: Dati giornalieri aggregati devono rispettare timezone locale del dispositivo.
- RB-011: Eliminazione voce pasto deve rigenerare aggregati giornata in modo atomico.
- RB-012: In caso di conflitto dati alimento duplicati, preferire record con completezza nutrienti maggiore.
- RB-013: Formula target kcal default: Mifflin-St Jeor per BMR, moltiplicatore livello attività per TDEE, delta calorico da obiettivo settimanale.
- RB-014: Sync Garmin automatica solo in evento apertura app (oltre al trigger manuale), non schedulazione periodica in MVP.
- RB-015: Override nutrienti mantiene solo l'ultimo valore valido (no storico versioni in MVP).

## Edge Cases
- EC-001: Barcode non trovato su cache e OFF.
- EC-002: OFF restituisce alimento senza porzioni dichiarate.
- EC-003: Unita porzione non standard (serving, cup) non mappata.
- EC-004: Nutrienti presenti con unita incoerenti (mg/g/ml).
- EC-005: Duplicati alimento con stesso barcode ma brand diversi.
- EC-006: Operazioni offline seguite da riapertura online.
- EC-007: Token Garmin scaduto durante sync periodico.
- EC-008: Giornata senza dati fitness ma con intake alimentare.
- EC-009: Macro impostati con somma diversa da 100.
- EC-010: Cambio fuso orario durante la giornata.
- EC-011: Inserimento quantita zero o negativa.
- EC-012: OFF non raggiungibile per timeout prolungato.

## Acceptance Criteria
- AC-001: L'utente completa onboarding con salvataggio profilo e target.
- AC-002: L'utente crea almeno una voce per ciascun tipo pasto nella stessa giornata.
- AC-003: Ricerca testo usa cache-first e fallback OFF tracciabile.
- AC-004: Ricerca barcode gestisce successo, non trovato e fallback manuale codice.
- AC-005: Dettaglio alimento aggiorna calorie/macro in tempo reale al cambio quantita.
- AC-006: Nutrienti mancanti visualizzati come N/D senza bloccare aggiunta alimento.
- AC-007: Override utente persistito e riutilizzato nelle ricerche successive.
- AC-008: Le percentuali macro non valide bloccano il salvataggio impostazioni.
- AC-009: Dashboard mostra card calorie con tutti i valori richiesti.
- AC-010: Dashboard mostra card macro con tre indicatori separati.
- AC-010A: Dashboard macro espone per ciascun macro sia grammi sia percentuali.
- AC-011: Dashboard mostra widget passi, peso, calorie esercizio, tempo allenamento.
- AC-012: Connessione Garmin da impostazioni completa e reversibile (disconnect).
- AC-013: Sync Garmin popola passi, calorie attive e minuti allenamento quando disponibili.
- AC-014: Stato ultimo sync e errore sync visibili all'utente.
- AC-014A: Errore sync Garmin mostrato come notifica temporanea non bloccante.
- AC-015: Consultazione storico giornaliero disponibile per almeno 90 giorni.
- AC-016: In offline l'utente puo aggiungere alimenti gia in cache e consultare dashboard.
- AC-017: Eliminazione voce pasto aggiorna aggregati calorie/macro in tempo reale.
- AC-018: Token integrazione salvati in storage sicuro e non in plain text.
- AC-019: Crash-free sessions in ambiente test >= soglia RNF prevista.
- AC-020: Tempo ricerca cache e salvataggio voce rispettano soglie prestazionali definite.
- AC-021: Eventi analytics tecnici principali tracciati con payload minimo richiesto.
- AC-022: Gestione timeout OFF con feedback utente e retry senza crash.
- AC-023: Sync Garmin automatica avviata all'apertura app se utente connesso.
- AC-024: Export dati utente disponibile e file generato correttamente.
- AC-025: Cancellazione completa dati utente locali eseguita con conferma e verifica post-azione.

## Rischi
- R-001: Eterogeneita dati OFF riduce accuratezza percepita.
- R-002: Ambiguita porzioni puo causare errori sistematici di logging.
- R-003: Dipendenza da OAuth Garmin puo introdurre ritardi rilascio.
- R-004: Sovraccarico informativo dashboard su schermi piccoli.
- R-005: Crescita cache locale senza politiche di manutenzione puo degradare performance.
- R-006: Mancata distinzione tra dati stimati e confermati puo creare confusione utente.

## Assunzioni
- A-001: Un solo profilo utente per installazione in MVP.
- A-002: OFF e la fonte esterna principale per catalogo alimenti.
- A-003: Garmin fornisce almeno dataset minimi richiesti nel contesto autorizzato.
- A-004: Il team dispone di un backend opzionale solo per analytics, non necessario per core offline.
- A-005: Formula calcolo target calorico e validata dal team prodotto prima sviluppo finale.

## Decisioni Utente Confermate (2026-03-28)
- D-001: Il prodotto deve permettere la scelta del profilo obiettivo (perdita/mantenimento/aumento).
- D-002: Formula target kcal delegata al team tecnico: adottata Mifflin-St Jeor + fattore attività + delta obiettivo settimanale.
- D-003: Storico oltre 90 giorni e requisito hard (MVP).
- D-004: Visualizzazione macro in entrambe le modalità: grammi e percentuali.
- D-005: Nutrienti mancanti mostrati in modo discreto (N/D non invasivo).
- D-006: Override nutrienti conserva solo ultimo valore (no storico revisioni in MVP).
- D-007: Pulsante rapido "+" in dashboard per inserimento alimento classificato come nice-to-have.
- D-008: Sync Garmin automatica all'apertura app approvata.
- D-009: Errore sync Garmin via notifica temporanea approvato.
- D-010: Export dati e diritto all'oblio richiesti in MVP.
- D-011: Fitbit e Google Fit confermati fuori scope MVP.
- D-012: KPI business non necessari in questa fase; validazione MVP iniziale tramite test manuale utente.

## Domande di Chiarimento (20)

### Strategia Prodotto
1. Qual e il segmento primario day-1: dimagrimento, mantenimento o sportivo performance?
2. Qual e il vincolo massimo di tempo onboarding accettabile?
3. Lo storico oltre 90 giorni e requisito hard o nice-to-have?
4. E prevista una modalita "solo calorie" senza dettaglio macro?

### Dati Nutrizionali
5. Quali micronutrienti sono mandatory a UI per MVP?
6. Come trattare alimenti con nutrienti implausibili (outlier estremi)?
7. L'override utente deve poter essere annullato singolarmente o solo reset totale?
8. Si vuole distinguere chiaramente alimento verificato vs non verificato?

### UX e Accessibilita
9. Scanner barcode sempre visibile in home o solo in contesto pasto?
10. Dashboard: ordine card fisso o personalizzabile?
11. Mostrare macro in grammi, percentuali o doppia vista di default?
12. Qual e il livello minimo di supporto accessibility (screen reader, contrasto)?

### Integrazioni
13. Frequenza sync Garmin desiderata (manuale, app-open, schedulata)?
14. In caso di sync parziale, mostrare warning persistente o toast temporaneo?
15. Devono essere sincronizzati anche tipi allenamento o solo aggregati?
16. In roadmap, priorita tra Fitbit e Google Fit quale e?

### Privacy, Compliance, Analytics
17. E richiesto export dati utente in MVP?
18. E richiesto diritto all'oblio completo in MVP?
19. Retention log analytics tecnica: quanti giorni?
20. KPI business non richiesti al momento: la validazione post-beta iniziale sara manuale.

## Sequenza Agenti e Deliverable per Fase
1. Fase INTAKE - Orchestrator
- Deliverable: obiettivo formalizzato, contesto, vincoli, session bootstrap.

2. Fase SPEC - Spec Agent
- Deliverable: spec funzionale, acceptance criteria machine-readable, rischi, assunzioni.

3. Fase ARCH - Architect
- Deliverable: architettura applicativa, modello componenti, decision record tecnici, strategia sicurezza.

4. Fase PLAN - Planner
- Deliverable: tasks.yaml, dipendenze, milestone, stima complessita.

5. Fase BUILD-CORE - Coder
- Deliverable: dominio pasti, storage locale, cache-first search, schermate base.

6. Fase BUILD-NUTRITION - Coder
- Deliverable: dettaglio alimento, porzioni, calcoli macro/calorie, override nutrienti.

7. Fase BUILD-INTEGRATION - Coder
- Deliverable: connessione Garmin, sync, gestione errori/token.

8. Fase VERIFY - QA/Test Agent
- Deliverable: test report su AC, regressione, bug list con severita.

9. Fase RELEASE - Release Agent
- Deliverable: build firmata, checklist release, note rilascio beta.

10. Fase CLOSE - Orchestrator
- Deliverable: report finale di conformita a Definition of Done e decisione go/no-go.

## Definition of Done
- Tutti i requisiti RF-001..RF-030 sono implementati oppure esplicitamente rinviati con decisione tracciata.
- Tutti gli AC-001..AC-025 sono verificabili con evidenza test/checklist.
- RNF critici (performance, sicurezza token, affidabilita offline) rispettati.
- Artifact di sessione aggiornati: spec.md, acceptance.json, status.json.
- Rischi maggiori con mitigazione documentata e ownership assegnata.

## Mappatura AC verso acceptance.json
La mappatura dettagliata di AC-001..AC-025 con precondizioni, metriche e checklist di validazione e definita in acceptance.json.

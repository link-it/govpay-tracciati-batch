# Proposta di intervento — Migrazione dell'elaborazione dei Tracciati di caricamento pendenze

> Stato: **DECISIONI CONSOLIDATE** (round di confronto completato) · Progetto: `govpay-tracciati-batch` · Riferimento issue: #1 (pipeline) + nuova issue di migrazione
>
> Scopo: portare la funzionalità di *elaborazione dei tracciati di caricamento pendenze* — oggi nel monolite GovPay (`it.govpay.core.business.Tracciati`) — in un **batch moderno, autonomo**, con **recovery dell'elaborazione, partizionamento e parallelismo interno**, mantenendo il supporto ai formati **CSV** e **JSON** e delegando la **produzione dei PDF degli avvisi al microservizio delle stampe** (`govpay-stampe-api`).

---

## 1. Analisi della funzionalità attuale (AS-IS)

### 1.1 Punto di ingresso e scheduling

- La classe `it.govpay.core.business.Tracciati` (`jars/core`, ~1310 righe) contiene tutta la logica.
- Orchestrazione in `it.govpay.core.business.Operazioni.elaborazioneTracciatiPendenze(IContext)`:
  - lock su DB con `BatchManager.startEsecuzione(BATCH_TRACCIATI)` (costante `caricamento-tracciati`);
  - seleziona i tracciati con `stato ∈ {ELABORAZIONE, IN_STAMPA}`, `tipo = PENDENZA`, a blocchi di 25;
  - per ciascuno costruisce `ElaboraTracciatoDTO` e chiama `Tracciati.elaboraTracciatoPendenze`;
  - cicla finché restano tracciati in quegli stati, poi `stopEsecuzione`.
- Trigger: task schedulati `ElaborazioneTracciatiPendenze` / `ElaborazioneTracciatiPendenzeCheck` (quest'ultimo parte solo se il flag `eseguiElaborazioneTracciati` è stato settato dall'upload).
- L'upload (`TracciatiDAO.create`) crea la riga con `stato = ELABORAZIONE` e `beanDati.stepElaborazione = NUOVO`, poi setta il flag.

### 1.2 Lock / lease multi-nodo (recovery del batch)

`BatchManager` — lock persistente sulla tabella `batch` (`cod_batch`, `nodo`, `inizio`, `aggiornamento`):
- `startEsecuzione`: `SELECT ... FOR UPDATE`; acquisisce se libero o **scaduto** (lease);
- heartbeat: `aggiornaEsecuzione` aggiorna `aggiornamento = now` durante l'elaborazione; se un nodo muore, dopo `getTimeoutBatch()` (default **5 min**) il batch è *stale* e recuperabile;
- `stopEsecuzione`: rilascia solo se il `nodo` coincide.

### 1.3 Flusso di elaborazione di un singolo tracciato

Due percorsi (`_elaboraTracciatoCSV` / `_elaboraTracciatoJSON`) con struttura simile:

**Fase A — Caricamento pendenze**
1. Prima esecuzione (`stepElaborazione = NUOVO`): inizializza i contatori nel `beanDati` (totali ADD/DEL) e passa a `IN_CARICAMENTO`.
2. **CSV**: split righe (`CSVUtils.splitCSV`), salta le già elaborate (`lineaElaborazioneAdd`), suddivide in lotti da `numeroVersamentiPerThread` (default 100) su N `CaricamentoTracciatoThread` (pool fixed, default 10). Ogni riga: trasformazione CSV→JSON con **template FreeMarker** (per tipo versamento/dominio), validazione, caricamento.
3. **JSON**: itera `inserimenti` (+ `annullamenti`), salta le linee già fatte, carica **in-line** con `OperazioneFactory.caricaVersamento`.
4. Ogni pendenza → una `Operazione` (`ADD`/`DEL`, `ESEGUITO_OK`/`ESEGUITO_KO`, richiesta/risposta JSON) su tabella `operazioni`.
5. Progresso salvato **a ogni step** aggiornando `beanDati` → **checkpoint a grana di riga**.
6. Fine caricamento → **esito**: JSON (`DettaglioTracciatoPendenzeEsito`) o CSV (template FreeMarker di risposta) in `raw_esito`.

**Fase B — Stampa avvisi** (solo se `beanDati.stampaAvvisi == true`)
1. `stato = IN_STAMPA`.
2. A pagine di 500, recupera i `Versamento` del tracciato con numero avviso, li suddivide in lotti da `numeroAvvisiDaStamparePerThread` (default 100) su `CreaStampeTracciatoThread` (pool fixed, default 10).
3. PDF generato **in-process** via `AvvisoPagamento.printAvviso*` (JasperReports, `jars/stampe`).
4. PDF scritti in uno **ZIP** in streaming su un `OutputStream` che punta al DB: PostgreSQL **Large Object** (`OID`); MySQL/Oracle/SQLServer/HSQL `BLOB`.
5. Deduplica avvisi/documenti (Set) per documenti multi-rata; `TracciatiPendenzeManager` coordina i thread (lock documento con `wait/notify`).
6. Fine stampa: `stato = COMPLETATO`, salva `zip_stampe`.

### 1.4 Modello dati (tabelle GovPay condivise)

- **`tracciati`**: `id`, `cod_dominio`, `cod_tipo_versamento`, `formato`, `tipo` (PENDENZA), `stato` (`STATO_ELABORAZIONE`: ELABORAZIONE/IN_STAMPA/COMPLETATO/SCARTATO), `descrizione_stato`, `data_caricamento`, `data_completamento`, `bean_dati` (TEXT/JSON), `raw_richiesta` (BYTEA), `raw_esito` (BYTEA), `zip_stampe` (**OID** su PostgreSQL), `id_operatore`.
- **`bean_dati`** = JSON `TracciatoPendenza`: contatori `numAdd*`/`numDel*`/`numStampe*`, checkpoint `lineaElaborazioneAdd`/`lineaElaborazioneDel`, `stepElaborazione` (`StatoTracciatoType`: NUOVO/IN_CARICAMENTO/CARICAMENTO_OK/CARICAMENTO_KO/ANNULLATO), `stampaAvvisi`, `dataUltimoAggiornamento`.
- **`operazioni`**: `id`, `id_tracciato`, `linea_elaborazione`, `tipo_operazione` (ADD/DEL/INC/N_V), `stato`, `dati_richiesta`/`dati_risposta` (BYTEA), `cod_versamento_ente`, `id_applicazione`, `cod_dominio`, `id_stampa`, `id_versamento`, `dettaglio_esito`.
- **`stampe`**: `id`, `id_versamento`, `id_documento`, `tipo` (AVVISO), `pdf` (BLOB), `data_creazione`.
- **Due livelli di stato**: `tracciati.stato` (macchina a stati del batch) + `beanDati.stepElaborazione` (avanzamento fine mostrato in UI/API).

### 1.5 Limiti dell'implementazione attuale (motivano la migrazione)

| # | Limite | Impatto |
|---|--------|---------|
| L1 | Orchestrazione a thread manuali con `Thread.sleep(2000)` di polling e `synchronized(this)` | fragile, spreca CPU, poco osservabile |
| L2 | Recovery "artigianale" via `lineaElaborazione` nel `beanDati` | logica intrecciata al business |
| L4 | Stampa PDF in-process (Jasper nel monolite) | accoppiamento, memoria, non scalabile a parte |
| L6 | Codice unico da 1300 righe con rami CSV/JSON quasi duplicati | manutenzione costosa |

> Nota: i limiti L3 (BLOB su DB) e L5 (mono-nodo) restano **accettati** per scelta (vedi Decisioni D4 e D5): non sono obiettivi di questa migrazione.

---

## 2. Proposta target (TO-BE)

### 2.1 Principi (allineati alle decisioni)

1. **Batch autonomo** Spring Boot con **Spring Batch** come motore (già in `pom.xml`), deployabile come immagine Docker indipendente (tooling in `docker/`). *(D1)*
2. **Riscrittura** dell'orchestrazione con Spring Batch, **riusando/portando le utility critiche** collaudate (generazione IUV e numero avviso pagoPA, `TracciatiConverter`, `PendenzaPostValidator`, trasformazioni FreeMarker CSV). *(D3 + D3a)*
3. **Tabelle GovPay condivise**: il batch legge/scrive sullo **stesso DB** e sulle tabelle esistenti `tracciati`/`operazioni`/`versamenti`/`stampe`; aggiorna `tracciati.stato` e `bean_dati` a ogni chunk così che la **console GovPay** continui a mostrare stato e progresso. *(D2 + D6 + D13)*
4. **Stampe via HTTP** verso `govpay-stampe-api` (stateless), con **client generato da OpenAPI** (v1.1.0). *(D9 + D10)*
5. **Un tracciato alla volta**, ma con **elaborazione interna parallela** (partizionamento degli step). *(D5)*
6. **Persistenza PDF/ZIP invariata**: singole `stampe` (BLOB) + ZIP sul tracciato (BLOB/Large Object), come oggi. *(D4)*
7. **Ambito**: solo tracciati di tipo **PENDENZA** (no notifica pagamenti). Formati **CSV/JSON**, **no XML**. *(D14 + D7)*

### 2.2 Architettura del Job

Un `Job` `elaborazioneTracciatoPendenzeJob` parametrizzato per `idTracciato` (identifying parameter → una sola istanza per tracciato), con step:

```
[Step 0] acquisizioneTracciato     (transizione a IN_CARICAMENTO; init contatori bean_dati)
   │
[Step 1] caricamentoPendenze       (PARTIZIONATO + chunk)
   │        ├─ Partitioner: divide righe CSV / elementi JSON in griglie
   │        ├─ ItemReader:  legge righe CSV o inserimenti/annullamenti JSON (paginato, ripartibile)
   │        ├─ ItemProcessor: valida + trasforma (CSV→JSON FreeMarker) → Versamento (utility portate)
   │        └─ ItemWriter:  carica pendenza su `versamenti` + scrive `Operazione` (upsert per linea)
   │
[Step 2] produzioneEsito           (genera raw_esito JSON o CSV via FreeMarker)
   │
[Step 3] stampaAvvisi              (condizionale su stampaAvvisi; PARTIZIONATO + chunk)
   │        ├─ Reader:    Versamenti del tracciato con numero avviso (paginato)
   │        ├─ Processor: mappa Versamento→PaymentNotice, chiama govpay-stampe-api → PDF
   │        └─ Writer:    salva `stampe` (BLOB) + accoda al ZIP; aggiorna contatori stampe
   │
[Step 4] finalizzazione            (stato COMPLETATO/SCARTATO, data_completamento, salva zip_stampe)
```

**Avvio** *(D11)*: un componente scheduler interno Spring seleziona i tracciati in `ELABORAZIONE`/`IN_STAMPA` (tipo PENDENZA) e lancia il job **sequenzialmente, uno alla volta** (`JobLauncher`). In più il batch espone un **endpoint REST** per l'avvio on-demand (come gli altri batch). Health/metriche via **Actuator** (già cablato nell'entrypoint Docker).

**Lock/lease** *(D5)*: essendo un tracciato alla volta, si mantiene un **lock globale con lease** (evoluzione di `BatchManager`, tabella `batch`): un solo nodo elabora, heartbeat periodico, recupero dei batch *stale* oltre timeout.

### 2.3 Partizionamento e parallelismo *(D5)*

- **Inter-tracciato**: **sequenziale** (un tracciato alla volta).
- **Intra-tracciato**: `Step 1` (caricamento) e `Step 3` (stampe) usano un `Partitioner` che spezza il lavoro in *grid* da N elementi; gli slave step girano su un `TaskExecutor` (pool configurabile) → sostituisce `CaricamentoTracciatoThread` / `CreaStampeTracciatoThread` e il polling `Thread.sleep`.

### 2.4 Recovery dell'elaborazione

- **Restartability nativa** Spring Batch: il `JobRepository` (tabelle `BATCH_JOB_*` / `BATCH_STEP_*`, sullo stesso DB GovPay) traccia i chunk committati; dopo un crash il job riparte dall'ultimo chunk.
- **Idempotenza a grana di riga**: l'`ItemWriter` fa *upsert* dell'`Operazione` per `(id_tracciato, linea_elaborazione)` (logica "operazione esistente → update" già presente oggi).
- **Sincronizzazione `bean_dati` (obbligatoria)** *(D6/D13)*: al commit di ogni chunk si aggiornano contatori e `stepElaborazione` nel `bean_dati` e il campo `tracciati.stato`, perché la **console GovPay** li usa per mostrare l'avanzamento all'utente. Questo garantisce anche la ripresa dei tracciati "in volo" al cut-over.
- **Lease globale**: heartbeat periodico; oltre `timeoutBatch` il batch è recuperabile da un altro nodo.

### 2.5 Supporto CSV e JSON *(D7: no XML)*

- Astrazione `TracciatoReader` con due implementazioni:
  - **CSV**: split righe + trasformazione FreeMarker per tipo versamento/dominio (utility portata da `TracciatiUtils.trasformazioneInputCSV`);
  - **JSON**: parsing di `TracciatoPendenzePost` (inserimenti + annullamenti).
- Output uniforme verso lo stesso `ItemProcessor`/`ItemWriter`.
- **XML**: non supportato (errore, come oggi).
- **Annullamenti (DEL)**: gestiti nel percorso JSON (il CSV supporta solo inserimenti), come oggi.

### 2.6 Stampa via microservizio `govpay-stampe-api` *(D9 + D10)*

- **Tutte le tipologie** oggi supportate: avviso **standard** (`POST /standard`), **bollettino postale** (flag `postal`), **bilingue** (`second_language`), **violazioni CDS** (`POST /cds_violation`).
- Il microservizio è **stateless**: richiede un JSON **auto-contenuto** `PaymentNotice` / `CdsViolation` (creditor/debtor/amount/notice_number/qrcode/iban/loghi), non le entità GovPay.
- **Client generato dall'OpenAPI** `govpay-stampe.yaml` v1.1.0 con `openapi-generator` (stesso approccio di `govpay-fdr-batch`), rigenerabile ad ogni versione del contratto.
- **Mapper `Versamento → PaymentNotice`**: nuovo, alimentato dai dati della pendenza; per QR pagoPA e numero avviso si riusano le utility portate (coerenza con IUV/avviso generati in fase di caricamento); loghi e dati creditore dalla configurazione di dominio. *(dettaglio D8 → §4)*
- Client HTTP resiliente (timeout, retry sui transitori — vedi D12); deduplica documenti multi-rata mantenuta lato batch.
- **Parallelismo delle chiamate** *(D15)*: **sincrono con pool bounded** — step stampe partizionato con `TaskExecutor` dimensionato da `pool.stampeAvvisiPagamento` e `numeroAvvisiDaStamparePerThread` (stesso tuning di oggi). Scartato il client reattivo `WebClient` perché stona col modello chunk bloccante di Spring Batch.

### 2.7 Persistenza dei PDF / esiti *(D4: invariata)*

- Singole `stampe` salvate su DB (`pdf` BLOB) come oggi.
- ZIP complessivo degli avvisi salvato sul tracciato (`zip_stampe`: BLOB / Large Object PostgreSQL), mantenendo la gestione per tipo DB. *(→ si valuterà se centralizzare i 5 rami vendor in un'unica utility, senza cambiare storage.)*
- Esito CSV/JSON in `raw_esito` (BYTEA), come oggi.

### 2.8 Gestione errori *(D12)*

- Su singola pendenza / singola stampa: si **prosegue** accumulando gli esiti KO nelle `Operazioni` (come oggi), con **retry automatico sui soli errori transitori** (lock DB, timeout/5xx HTTP verso stampe) tramite retry/skip policy di Spring Batch. Nessun fail-fast dell'intero tracciato per un KO di riga.

### 2.9 Osservabilità

- Metriche Spring Batch + Micrometer/Prometheus (già in `pom.xml`) e Actuator (health/readiness).

---

## 3. Mappatura AS-IS → TO-BE

| Componente attuale | Sostituito da |
|--------------------|---------------|
| `Operazioni.elaborazioneTracciatiPendenze` (selezione + loop) | Scheduler interno + `JobLauncher` (1 tracciato alla volta) + endpoint REST di avvio |
| `BatchManager` (lock globale batch) | Lock globale con lease (mantenuto) + `JobRepository` per il restart |
| `CaricamentoTracciatoThread` + pool | `Step` partizionato + chunk (ItemReader/Processor/Writer) |
| `CreaStampeTracciatoThread` + pool | `Step` stampe partizionato + client HTTP `govpay-stampe-api` |
| `AvvisoPagamento.printAvviso*` (Jasper in-process) | Chiamata REST al microservizio stampe |
| `OperazioneFactory.caricaVersamento`/`elaboraLineaCSV` | Orchestrazione riscritta + **utility portate** (IUV/avviso, converter, validator) |
| Checkpoint `beanDati.lineaElaborazione*` | Restartability Spring Batch + upsert Operazione + **sync `bean_dati`** (per console) |
| ZIP su BLOB/Large Object | Invariato (DB), con utility unica per i rami vendor |
| Esito CSV/JSON via FreeMarker | Step `produzioneEsito` (utility FreeMarker portate) |

**Parametri di configurazione** da riportare nel nuovo batch (dettaglio ed esito property-per-property in **Appendice B**):

| Ambito | Property da mantenere | Note |
|--------|-----------------------|------|
| Parallelismo caricamento | `...numeroVersamentiPerThread` (100), `...thread.pool.caricamentoTracciati` (10) | chunk/grid size + `TaskExecutor` step caricamento |
| Parallelismo stampe | `...numeroAvvisiDaStamparePerThread` (100), `...thread.pool.caricamentoTracciati.stampeAvvisiPagamento` (10) | concorrenza chiamate al microservizio stampe (vedi **D15**) |
| Gestione pendenze | `defaultCustomIuvGenerator.class`, `codTipoVersamentoPerPagamentiLiberi`, `codTipoVersamentoPerTipiPendenzeNonCensiti`, `censimentoAutomaticoTipiPendenza.enabled` | necessarie alle utility di caricamento portate |
| Payload avviso | `...sanp24.giorniValidita...`, `...avvisoPagamento.identificativoDebitore.nascondiKeyword` | alimentano il payload verso il microservizio |
| Client stampe *(nuovo)* | URL / timeout / retry `govpay-stampe-api` | da definire |
| **Rimosse** | `it.govpay.batchOn`, `...caricamentoTracciati.enabled` (attivazione: il batch è attivo se deployato); `it.govpay.resource.path` (niente più customizzazione properties per dominio/tributo) | — |
| **Da valutare** | `it.govpay.clusterId`, `it.govpay.timeoutBatch` (lock/lease) | potenzialmente superate dal `JobRepository` Spring Batch (R2) |

---

## 4. Registro delle decisioni

| ID | Tema | Decisione |
|----|------|-----------|
| **D1** | Motore batch | **Spring Batch** (JobRepository sul DB GovPay, restart nativo). |
| **D2** | Modello dati | **Tabelle GovPay condivise** (`tracciati`/`operazioni`/`versamenti`/`stampe`), stesso DB. |
| **D3** | Riuso logica caricamento | **Riscrittura** dell'orchestrazione nel batch. |
| **D3a** | Profondità riscrittura | **Porting delle utility critiche** (IUV/numero avviso pagoPA, converter pendenza, validatori, FreeMarker); si riscrive l'orchestrazione, non gli algoritmi. |
| **D4** | Storage PDF/ZIP | **Invariato**: tutto su DB (singole `stampe` BLOB + ZIP BLOB/OID). |
| **D5** | Parallelismo | **Un tracciato alla volta**, con **elaborazione interna parallela** (partizionamento). Lock globale con lease. |
| **D6** | Stato legacy | **`bean_dati` + `tracciati.stato` aggiornati a ogni chunk** (usati dalla console GovPay). |
| **D7** | Formato XML | **Non supportato** (solo CSV/JSON). |
| **D8** | Mapper stampe | Mapper **nuovo** `Versamento → PaymentNotice`; QR/avviso dalle utility portate, loghi/creditore da config dominio. *(dettagli residui sotto)* |
| **D9** | Tipologie avviso | **Tutte** (standard, postale, bilingue, violazioni CDS), come oggi. |
| **D10** | Client stampe | **Generato da OpenAPI** (`govpay-stampe.yaml` v1.1.0). |
| **D11** | Trigger/deploy | **Daemon con scheduler interno Spring + Actuator**, avviabile **on-demand via API REST**. |
| **D12** | Errori/retry | **Prosegui accumulando esiti KO + retry automatico sui soli transitori**. |
| **D13** | Cut-over | **Switch netto**: vecchia procedura spenta, il nuovo batch riprende anche i tracciati in volo via `bean_dati`; stato mantenuto per la console. |
| **D14** | Ambito | **Solo caricamento pendenze** (tipo PENDENZA); no notifica pagamenti. |
| **D15** | Parallelismo chiamate stampe | **Sincrono con pool bounded**: step stampe partizionato con `TaskExecutor` dimensionato da `pool.stampeAvvisiPagamento` + `numeroAvvisiDaStamparePerThread`. Coerente col modello chunk (bloccante) di Spring Batch, riusa 1:1 il tuning attuale, retry/commit del chunk semplici. Scartato il client reattivo (`WebClient`). |

### Punti di dettaglio residui (da chiarire in fase implementativa, non bloccanti)

- **R1 (da D3a/D8)** — Perimetro esatto delle utility da portare: confermare la lista (generazione IUV, generazione numero avviso, `TracciatiConverter`, `PendenzaPostValidator`, `TrasformazioniUtils` FreeMarker) e la loro estraibilità da `govpay-core`/`govpay-common` senza trascinare l'intero monolite. Valutare se pubblicarle come modulo condiviso.
- **R2 (da D2)** — Ammissibilità delle tabelle `BATCH_*` di Spring Batch sullo schema GovPay (naming/segregazione); in alternativa schema dedicato per il solo JobRepository sullo stesso DB.
- **R3 (da D8)** — Origine di **loghi ente**, dati creditore e stringa **QR pagoPA** per il payload del microservizio (ricalcolo vs lettura da GovPay/config dominio).
- **R4 (da D11)** — Contratto dell'endpoint REST di avvio on-demand (autenticazione, parametri: per dominio/per idTracciato/tutti), coerente con gli altri batch.
- **R5 (da D10)** — URL di deploy del microservizio stampe negli ambienti e modalità di autenticazione.
- **R6 (da D4)** — Se centralizzare i 5 rami vendor di scrittura BLOB/Large Object in un'unica utility (senza cambiare lo storage).

---

## 5. Roadmap proposta

1. **Fondazioni**: accesso dati sulle tabelle GovPay condivise + `JobRepository`; porting/estrazione delle utility critiche (R1).
2. **Step di caricamento** (CSV+JSON) partizionato con restart + upsert Operazione + sync `bean_dati`.
3. **Produzione esito** (JSON/CSV via FreeMarker).
4. **Integrazione microservizio stampe**: client da OpenAPI + mapper `Versamento→PaymentNotice` (tutte le tipologie) + step stampe.
5. **Persistenza PDF/ZIP** su DB (utility unica per i rami vendor).
6. **Scheduler interno + endpoint REST di avvio** + lock/lease globale + osservabilità.
7. **Test di ripartenza** (crash a metà caricamento e a metà stampa) e di parallelismo interno.
8. **Cut-over** (switch netto, ripresa tracciati in volo, spegnimento vecchia procedura).

---

### Appendice B — Proprietà `GovpayConfig` utilizzate dal flusso (verificate sul call-graph)

Inventario **verificato** delle letture di `it.govpay.core.utils.GovpayConfig` raggiungibili dalla vecchia procedura e dalle utility richiamate (15 proprietà distinte), con l'**esito deciso** per il nuovo batch. Colonna *Esito*: `MANTIENI` = riportata nel nuovo batch · `RIMUOVI` = non riportata · `VALUTA` = dipende dal meccanismo scelto.

**Gating / abilitazione** (`ElaborazioneTracciatiPendenze[Check]`) — **RIMOSSE**: il batch è attivo se deployato, non serve un flag di abilitazione.
| getter | property | default | tipo | esito |
|---|---|---|---|---|
| `isBatchOn()` | `it.govpay.batchOn` | `true` | boolean | **RIMUOVI** |
| `isBatchCaricamentoTracciati()` | `it.govpay.batch.caricamentoTracciati.enabled` | `false` | boolean | **RIMUOVI** |

**Orchestrazione / lock-lease** (`Operazioni`, `BatchManager`)
| getter | property | default | tipo | esito |
|---|---|---|---|---|
| `getClusterId()` | `it.govpay.clusterId` | `null` | String | **VALUTA** (lock/lease legacy, potenzialmente superato dal `JobRepository` Spring Batch — vedi R2) |
| `getTimeoutBatch()` | `it.govpay.timeoutBatch` | `300000` ms (property in secondi ×1000; fallback 5 min) | long | **VALUTA** (idem) |

**Parallelismo** (`Tracciati`, `ThreadExecutorManager`) — **MANTIENI**
| getter | property | default | tipo | esito |
|---|---|---|---|---|
| `getBatchCaricamentoTracciatiNumeroVersamentiDaCaricarePerThread()` | `it.govpay.batch.caricamentoTracciati.numeroVersamentiPerThread` | `100` | Integer | **MANTIENI** (chunk/grid caricamento) |
| `getBatchCaricamentoTracciatiNumeroAvvisiDaStamparePerThread()` | `it.govpay.batch.caricamentoTracciati.numeroAvvisiDaStamparePerThread` | `100` | Integer | **MANTIENI** (chunk/grid stampe) |
| `getDimensionePoolCaricamentoTracciati()` | `it.govpay.thread.pool.caricamentoTracciati` | `10` | int | **MANTIENI** (pool caricamento) |
| `getDimensionePoolCaricamentoTracciatiStampaAvvisi()` | `it.govpay.thread.pool.caricamentoTracciati.stampeAvvisiPagamento` | `10` | int | **MANTIENI** (concorrenza chiamate stampe — vedi D15) |

**Ramo caricamento pendenza** (`OperazioneFactory → Versamento → Iuv/VersamentoUtils`) — **MANTIENI** (gestione pendenze)
| getter | property | default | tipo | esito |
|---|---|---|---|---|
| ⚠️ `getDefaultCustomIuvGenerator()` | `it.govpay.defaultCustomIuvGenerator.class` | `new CustomIuv()` | classe (prefisso IUV) | **MANTIENI** |
| `getCodTipoVersamentoPendenzeLibere()` | `it.govpay.versamenti.codTipoVersamentoPerPagamentiLiberi` | `"LIBERO"` (required) | String | **MANTIENI** |
| `getCodTipoVersamentoPendenzeNonCensite()` | `it.govpay.versamenti.codTipoVersamentoPerTipiPendenzeNonCensiti` | `"LIBERO"` (required) | String | **MANTIENI** |
| `isCensimentoTipiVersamentoSconosciutiEnabled()` | `it.govpay.versamenti.censimentoAutomaticoTipiPendenza.enabled` | `false` | boolean | **MANTIENI** |

**Ramo stampa avviso** (`AvvisoPagamento → AvvisoPagamento[V2]Utils → LabelAvvisiProperties`)
| getter | property | default | tipo | esito |
|---|---|---|---|---|
| `getNumeroGiorniValiditaPendenza()` | `it.govpay.modello3.sanp24.giorniValiditaDaAssegnarePendenzaSenzaDataValidita` | `null` | Integer | **MANTIENI** (payload avviso) |
| `getKeywordsDaSostituireIdentificativiDebitoreAvviso()` | `it.govpay.stampe.avvisoPagamento.identificativoDebitore.nascondiKeyword` | `[]` | List\<String> | **MANTIENI** (payload avviso) |
| `getResourceDir()` | `it.govpay.resource.path` | `null` | String | **RIMUOVI** (non c'è più la customizzazione delle properties per dominio/tributo) |

**Note di verifica**
- Classi del flusso che **non** leggono `GovpayConfig` (quindi facili da portare): `CaricamentoTracciatoThread`, `CreaStampeTracciatoThread`, `TracciatiConverter`, `TracciatiUtils`, `TracciatiPendenzeManager`, `PendenzaPostValidator`, `TrasformazioniUtils`, modulo `jars/stampe`.
- **IUV/numero avviso**: unica property sul percorso IUV è `getDefaultCustomIuvGenerator()` (prefisso). Il resto (`auxDigit`/`applicationCode`/`segregationCode`) deriva dai dati del dominio in `IuvBD`/`IuvUtils`, senza property → riduce il rischio di R1.
- **Ramo stampa** (delegato al microservizio, D9): si mantengono lato batch solo le property che alimentano il **payload** dell'avviso (`giorniValidita...`, `nascondiKeyword` debitore). `resource.path` è **rimossa** (niente più customizzazione properties per dominio/tributo). Il modulo `jars/stampe` ha inoltre una config propria `avvisoPagamento.properties` (`AvvisoPagamentoProperties`), distinta da `GovpayConfig`, ora responsabilità del microservizio.
- Escluse perché non raggiungibili da questo flusso (metodi condivisi di altri rami): `getNavSondaPagoPA()`, `isAggiornamentoValiditaMandatorio()`; nota: `VersamentiBD.getMaxRisultati()` appartiene a `it.govpay.bd.GovpayConfig` (classe **diversa** da quella in esame).

### Appendice A — File analizzati nel monolite GovPay

- `jars/core/.../business/Tracciati.java`, `Operazioni.java`, `BatchManager.java`, `AvvisoPagamento.java`
- `jars/core/.../utils/thread/CaricamentoTracciatoThread.java`, `CreaStampeTracciatoThread.java`, `ThreadExecutorManager.java`
- `jars/core/.../utils/TracciatiConverter.java`, `utils/tracciati/TracciatiUtils.java`, `TracciatiPendenzeManager.java`
- `jars/core/.../utils/tasks/ElaborazioneTracciatiPendenze*.java`, `utils/GovpayConfig.java`
- `jars/core/.../dao/pagamenti/TracciatiDAO.java`, `dto/ElaboraTracciatoDTO.java`, `dto/PostTracciatoDTO.java`
- `jars/orm-beans/.../model/Tracciato.java`, `Operazione.java`, `Stampa.java`
- `jars/core-beans/.../beans/tracciati/TracciatoPendenza.java`
- `src/main/resources/db/sql/postgresql/gov_pay.sql` (DDL `tracciati`, `operazioni`, `stampe`)
- `jars/stampe` (generazione PDF Jasper in-process)
- `govpay-stampe-api/src/main/resources/govpay-stampe.yaml` (contratto microservizio stampe v1.1.0)

<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay - Porta di accesso al sistema pagoPA - Tracciati Batch

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=link-it_govpay-tracciati-batch&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=link-it_govpay-tracciati-batch)
[![Docker Hub](https://img.shields.io/docker/v/linkitaly/govpay-tracciati-batch?label=Docker%20Hub&logo=docker)](https://hub.docker.com/r/linkitaly/govpay-tracciati-batch)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://raw.githubusercontent.com/link-it/govpay-tracciati-batch/main/LICENSE)
[![Spring Boot](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fraw.githubusercontent.com%2Flink-it%2Fgovpay-bom%2Fmain%2Fpom.xml&query=%2F%2F*%5Blocal-name()%3D'spring-boot.version'%5D&label=Spring%20Boot&color=brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

Batch Spring Boot che elabora i tracciati di caricamento massivo delle posizioni debitorie (pendenze) di GovPay: carica e annulla le pendenze, produce l'esito e, se richiesto, gli avvisi di pagamento in PDF.

## Sommario

Il batch porta fuori dal monolite GovPay l'elaborazione dei tracciati di caricamento pendenze (`it.govpay.core.business.Tracciati`), con ripresa delle elaborazioni interrotte, parallelismo interno e stampa degli avvisi delegata a [govpay-stampe-api](https://github.com/link-it/govpay-stampe-api). L'analisi e le decisioni di progetto sono in [docs/proposta-migrazione-elaborazione-tracciati.md](docs/proposta-migrazione-elaborazione-tracciati.md).

I tracciati sono caricati in GovPay dall'API Backoffice (`POST /pendenze/tracciati`) e dalla console, che li registrano nella tabella `tracciati` in stato `ELABORAZIONE`. Il batch esegue un job Spring Batch, `elaborazioneTracciatiPendenzeJob`: a ogni esecuzione prende il tracciato di tipo `PENDENZA` in stato `ELABORAZIONE` o `IN_STAMPA` con l'id più basso e lo porta avanti, aggiornando `tracciati.stato` e `bean_dati` perché la console continui a mostrarne l'avanzamento. Un'esecuzione elabora un solo tracciato: con lo scheduler interno, di default ogni minuto.

Il job si avvia da uno scheduler interno (modalità di default), una sola volta con il profilo `cron`, oppure a richiesta con l'API REST descritta in [Avvio manuale via REST](#avvio-manuale-via-rest). Le esecuzioni sono coordinate tra più nodi tramite i metadati Spring Batch sul database.

> **Stato dello sviluppo.** Su `main` il caricamento vero e proprio non è ancora implementato: `CaricamentoService` ha solo un'implementazione segnaposto (`SeamStubConfiguration`) che restituisce esito negativo per ogni pendenza, e i tracciati in formato CSV non sono supportati (l'esecuzione fallisce e il tracciato viene scartato). Il batch non va quindi ancora attivato al posto dell'elaborazione del monolite. Il lavoro in corso è sui rami di sviluppo del repository.

Storico delle modifiche nel [ChangeLog](ChangeLog).

## Integrazione con GovPay

| Componente | Quando serve | Obbligatorio | Ruolo |
|------------|--------------|--------------|-------|
| Database di GovPay | runtime | sì | Legge `tracciati`, `operazioni`, `versamenti`, `documenti`, `domini` (dati dell'ente e logo), `connettori` e `configurazione`; scrive `tracciati` (`stato`, `descrizione_stato`, `data_completamento`, `bean_dati`, `raw_esito`, `file_name_esito`, `zip_stampe`), `operazioni` (una riga per linea del tracciato) e `stampe` (PDF degli avvisi). Le tabelle sono create dagli script di installazione di GovPay, il batch non crea lo schema |
| Tabelle Spring Batch | runtime | sì | Metadati delle esecuzioni (`batch_job_*`, `batch_step_*`), usati anche per evitare esecuzioni concorrenti tra nodi. Lo schema è nel pacchetto `sql.zip` (vedi [Compilazione](#compilazione)) e l'immagine Docker può crearlo all'avvio |
| [govpay-bom](https://github.com/link-it/govpay-bom) | build | sì | Parent Maven: versioni delle dipendenze e dei plugin, profilo `dist` |
| [govpay-common](https://github.com/link-it/govpay-common) | build | sì | Libreria comune: entità di anagrafica, connettori, coordinamento dei job, API REST del batch, client GDE, transaction id e livelli di log da database, script di svecchiamento dei metadati |
| API Backoffice e console di GovPay | runtime | sì | Caricano i tracciati in `tracciati` (stato `ELABORAZIONE`) e ne mostrano avanzamento ed esito a partire da `stato` e `bean_dati` |
| GovPay (monolite) | runtime | no | Oggi esegue la stessa elaborazione (`it.govpay.batch.caricamentoTracciati.enabled`). Il passaggio al batch è previsto come switch netto: la procedura del monolite va spenta quando il batch viene attivato |
| [govpay-stampe-api](https://github.com/link-it/govpay-stampe-api) | runtime | no | Produce i PDF degli avvisi (`POST /standard`) per i tracciati caricati con la stampa degli avvisi. URL in `govpay.tracciati.stampe.base-url`, chiamato senza autenticazione. Il contratto è in `src/main/resources/openapi/govpay-stampe.yaml`, da cui la build genera i modelli |
| [govpay-gde-api](https://github.com/link-it/govpay-gde-api) | runtime | no | Riceve un evento interno (`elaborazioneTracciatoPendenze`, esito `OK` o `KO`) alla fine dell'elaborazione di un tracciato, se il connettore `govpay_gde_api` è abilitato in GovPay. Un GDE non raggiungibile non blocca il batch |
| [govpay-console-api](https://github.com/link-it/govpay-console-api) | runtime | no | Può avviare il batch e consultarne le esecuzioni tramite l'API REST, se il batch è aggiunto al catalogo delle operazioni (`govpay.operazioni.catalogo`) |

Le modifiche ai livelli di log fatte dalla console (riga `log_level` della tabella `configurazione`) raggiungono il batch entro l'intervallo di refresh, senza riavvio.

## Compilazione

Requisiti: JDK 21 e Maven 3.8.1 o successivo.

```bash
mvn clean install -P jar     # fat jar, per esecuzione standalone e container (profilo di default)
mvn clean install -P war     # war, per il deploy su application server
```

Il profilo sceglie il packaging; il risultato è in `target/govpay-tracciati-batch.jar` o `target/govpay-tracciati-batch.war`. In entrambi i casi i driver JDBC non sono inclusi. La build esegue i test e l'analisi OWASP Dependency-Check delle dipendenze; per escludere l'analisi OWASP, per esempio in sviluppo:

```bash
mvn clean install -P jar -Dowasp.phase=none
```

Per produrre anche `target/sql.zip`, con lo schema Spring Batch per ogni database e gli script di svecchiamento dei metadati, usato dall'immagine Docker e allegato alle release:

```bash
mvn clean install -Pjar,dist
```

Il profilo `jar` va indicato insieme a `dist`: è quello che lascia i driver JDBC fuori dal jar.

## Configurazione

Le proprietà si impostano in `application.properties`, da riga di comando (`-D<proprietà>=<valore>`) o con le variabili d'ambiente equivalenti di Spring Boot, indicate nelle tabelle. Con l'immagine Docker si usano anche le variabili descritte in [Esecuzione con Docker](#esecuzione-con-docker).

### Elaborazione dei tracciati

| Proprietà | Variabile d'ambiente | Default | Descrizione |
|-----------|----------------------|---------|-------------|
| `scheduler.elaborazioneTracciatiPendenzeJob.fixedDelayString` | `SCHEDULER_ELABORAZIONETRACCIATIPENDENZEJOB_FIXEDDELAYSTRING` | `60000` | Intervallo tra la fine di un'esecuzione e l'avvio della successiva, in ms. Ogni esecuzione elabora un tracciato |
| `scheduler.initialDelayString` | `SCHEDULER_INITIALDELAYSTRING` | `1` | Ritardo della prima esecuzione dopo l'avvio, in ms |
| `govpay.batch.caricamento-chunk-size` | `GOVPAY_BATCH_CARICAMENTOCHUNKSIZE` | `100` | Righe del tracciato per lotto di caricamento; i lotti sono elaborati in parallelo |
| `govpay.batch.caricamento-pool-size` | `GOVPAY_BATCH_CARICAMENTOPOOLSIZE` | `10` | Lotti di caricamento elaborati in parallelo |
| `govpay.batch.stampe-chunk-size` | `GOVPAY_BATCH_STAMPECHUNKSIZE` | `100` | Avvisi per lotto di stampa; i lotti sono elaborati in parallelo |
| `govpay.batch.stampe-pool-size` | `GOVPAY_BATCH_STAMPEPOOLSIZE` | `10` | Lotti di stampa elaborati in parallelo |
| `govpay.batch.modalita-zip-stampe` | `GOVPAY_BATCH_MODALITAZIPSTAMPE` | `AUTO` | Scrittura dello ZIP degli avvisi in `tracciati.zip_stampe`: `AUTO` (dedotta dal database), `LARGE_OBJECT` (OID PostgreSQL), `BLOB` |

### Stampa degli avvisi

| Proprietà | Variabile d'ambiente | Default | Descrizione |
|-----------|----------------------|---------|-------------|
| `govpay.tracciati.stampe.base-url` | `GOVPAY_TRACCIATI_STAMPE_BASEURL` | - | URL base di govpay-stampe-api (es. `http://govpay-stampe-api:8080`): il batch vi aggiunge `/standard`. Va impostata se si caricano tracciati con la stampa degli avvisi: senza, ogni stampa fallisce ed è contata come KO |
| `govpay.tracciati.stampe.connect-timeout-ms` | `GOVPAY_TRACCIATI_STAMPE_CONNECTTIMEOUTMS` | `10000` | Timeout di connessione verso govpay-stampe-api, in ms |
| `govpay.tracciati.stampe.read-timeout-ms` | `GOVPAY_TRACCIATI_STAMPE_READTIMEOUTMS` | `60000` | Timeout di lettura del PDF, in ms |
| `govpay.tracciati.stampe.logo-default-path` | `GOVPAY_TRACCIATI_STAMPE_LOGODEFAULTPATH` | `classpath:stampe/logo-default.png` | Logo usato per gli enti che non hanno un logo in `domini`. La risorsa di default non è presente nel jar: all'avvio compare un avviso e, per gli enti senza logo, la stampa fallisce perché govpay-stampe-api richiede il logo (`first_logo`) |

### Esecuzione e cluster

| Proprietà | Variabile d'ambiente | Default | Descrizione |
|-----------|----------------------|---------|-------------|
| `govpay.batch.cluster-id` | `GOVPAY_BATCH_CLUSTERID` | `GovPay-Tracciati-Batch` | Identificativo del nodo, registrato nelle esecuzioni e negli eventi GDE; nel container è l'indirizzo IP |
| `govpay.batch.stale-threshold-minutes` | `GOVPAY_BATCH_STALETHRESHOLDMINUTES` | `120` | Minuti senza aggiornamenti dopo cui un'esecuzione in corso è considerata bloccata e può essere superata |
| `govpay.batch.time-zone` | `GOVPAY_BATCH_TIMEZONE` | `Europe/Rome` | Fuso orario di default della JVM e delle date delle esecuzioni |
| `govpay.batch.job-repository.jdbc.enabled` | `GOVPAY_BATCH_JOBREPOSITORY_JDBC_ENABLED` | `true` | Metadati Spring Batch su database (da `govpay-common`); senza, il coordinamento tra nodi non funziona |
| `spring.batch.job.enabled` | `SPRING_BATCH_JOB_ENABLED` | `false` | Avvio automatico dei job da parte di Spring Boot: resta `false`, i job sono avviati dallo scheduler o dal profilo `cron` |
| `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` | `default` | `cron` per eseguire il job una volta e terminare (vedi [Esecuzione](#esecuzione)); `local` per lo sviluppo, con `application-local.properties` |
| `server.port` | `SERVER_PORT` | `8080` (`10001` nel container) | Porta HTTP dell'API REST del batch e degli endpoint actuator |
| `spring.application.name` | `SPRING_APPLICATION_NAME` | `govpay-tracciati-batch` | Nome dell'applicazione nei log |
| `spring.main.banner-mode` | `SPRING_MAIN_BANNERMODE` | `off` | Banner di Spring Boot all'avvio |
| `spring.jackson.default-property-inclusion` | `SPRING_JACKSON_DEFAULTPROPERTYINCLUSION` | `non_null` | Esclude i campi nulli dal JSON |
| `spring.jackson.time-zone` | `SPRING_JACKSON_TIMEZONE` | `Europe/Rome` | Fuso orario delle date serializzate in JSON |

### Connettori e client HTTP

URL e autenticazione del GDE non sono proprietà del batch: sono nel connettore `govpay_gde_api` di GovPay (tabella `connettori`).

| Proprietà | Variabile d'ambiente | Default | Descrizione |
|-----------|----------------------|---------|-------------|
| `govpay.client.cache.enabled` | `GOVPAY_CLIENT_CACHE_ENABLED` | `false` | Carica i connettori all'avvio e li tiene in cache (da `govpay-common`) |
| `govpay.client.async.core-pool-size` | `GOVPAY_CLIENT_ASYNC_COREPOOLSIZE` | `10` | Thread minimi per l'invio asincrono degli eventi al GDE |
| `govpay.client.async.max-pool-size` | `GOVPAY_CLIENT_ASYNC_MAXPOOLSIZE` | `50` | Thread massimi per l'invio asincrono degli eventi al GDE |
| `govpay.client.async.queue-capacity` | `GOVPAY_CLIENT_ASYNC_QUEUECAPACITY` | `100` | Eventi in coda in attesa di invio |
| `govpay.client.async.thread-name-prefix` | `GOVPAY_CLIENT_ASYNC_THREADNAMEPREFIX` | `async-http-` | Prefisso dei thread di invio |

### Database

| Proprietà | Variabile d'ambiente | Default | Descrizione |
|-----------|----------------------|---------|-------------|
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | - | URL JDBC del database di GovPay (obbligatoria con il jar) |
| `spring.datasource.jndi-name` | `SPRING_DATASOURCE_JNDINAME` | - | Nome JNDI del datasource, per il deploy del war su application server (es. `java:comp/env/jdbc/govpay`); se impostata, le altre proprietà `spring.datasource.*` sono ignorate |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | - | Utente del database |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | - | Password del database |
| `spring.datasource.driver-class-name` | `SPRING_DATASOURCE_DRIVERCLASSNAME` | dedotto dall'URL | Classe del driver JDBC |
| `spring.jpa.database-platform` | `SPRING_JPA_DATABASEPLATFORM` | dedotto dal driver | Dialetto Hibernate |
| `spring.jpa.properties.hibernate.dialect` | `SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT` | dedotto dal driver | Dialetto Hibernate |
| `spring.jpa.hibernate.ddl-auto` | `SPRING_JPA_HIBERNATE_DDLAUTO` | `none` (`create-drop` con database embedded) | Gestione dello schema: lasciare `none` o `validate`, lo schema è di GovPay |
| `spring.datasource.hikari.minimum-idle` | `SPRING_DATASOURCE_HIKARI_MINIMUMIDLE` | `10` (`2` nel container) | Connessioni minime inattive del pool |
| `spring.datasource.hikari.maximum-pool-size` | `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE` | `10` | Connessioni massime del pool: con i valori di default, caricamento e stampe usano fino a 10 thread in parallelo |
| `spring.datasource.hikari.connection-timeout` | `SPRING_DATASOURCE_HIKARI_CONNECTIONTIMEOUT` | `30000` (`20000` nel container) | Attesa massima di una connessione, in ms |
| `spring.datasource.hikari.idle-timeout` | `SPRING_DATASOURCE_HIKARI_IDLETIMEOUT` | `600000` (`10000` nel container) | Tempo dopo cui una connessione inattiva viene chiusa, in ms |
| `spring.datasource.hikari.max-lifetime` | `SPRING_DATASOURCE_HIKARI_MAXLIFETIME` | `1800000` (`600000` nel container) | Durata massima di una connessione, in ms |

I driver JDBC non sono inclusi né nel jar né nel war: vedi [Esecuzione](#esecuzione). Su PostgreSQL lo ZIP degli avvisi è scritto come Large Object, sugli altri database come Blob; il progressivo IUV, quando il caricamento sarà implementato, userà la tabella `ID_MESSAGGIO_RELATIVO` di GovPay.

### Logging

| Proprietà | Variabile d'ambiente | Default | Descrizione |
|-----------|----------------------|---------|-------------|
| `logging.file.name` | `LOGGING_FILE_NAME` | - (solo console) | File di log; nel container `/var/log/govpay-tracciati/govpay-tracciati.log` |
| `logging.level.it.govpay` | `LOGGING_LEVEL_IT_GOVPAY` | `INFO` | Livello di log dei package GovPay |
| `logging.pattern.level` | `LOGGING_PATTERN_LEVEL` | `%5p [%X{transactionId:-},%X{correlationId:-}]` | Aggiunge a ogni riga transaction id e correlation id dell'esecuzione: il correlation id è quello della richiesta di avvio manuale, oppure generato per gli avvii schedulati |
| `govpay.logging.livelli-dinamici.enabled` | `GOVPAY_LOGGING_LIVELLIDINAMICI_ENABLED` | `true` | Livelli di log letti dalla riga `log_level` della tabella `configurazione` |
| `govpay.logging.livelli-dinamici.intervallo-refresh` | `LOG_LEVEL_REFRESH_INTERVAL` | `60s` | Intervallo di rilettura dei livelli dal database; `0` li legge solo all'avvio |
| `govpay.logging.livelli-dinamici.ritardo-iniziale` | `GOVPAY_LOGGING_LIVELLIDINAMICI_RITARDOINIZIALE` | `60s` | Ritardo della prima rilettura dopo quella di avvio |
| `govpay.logging.livelli-dinamici.logger-gestiti[0]` | `GOVPAY_LOGGING_LIVELLIDINAMICI_LOGGERGESTITI_0_` | `it.govpay` | Prefissi dei logger modificabili da database |
| `govpay.logging.tracciatura.enabled` | `GOVPAY_LOGGING_TRACCIATURA_ENABLED` | `true` | Transaction id e correlation id su richieste REST, chiamate in uscita e thread paralleli (da `govpay-common`) |
| `govpay.logging.tracciatura.ordine` | `GOVPAY_LOGGING_TRACCIATURA_ORDINE` | `-2147483548` | Ordine del filtro nella catena servlet |
| `govpay.logging.tracciatura.lunghezza-massima-id` | `GOVPAY_LOGGING_TRACCIATURA_LUNGHEZZAMASSIMAID` | `128` | Lunghezza massima di un identificativo ricevuto dall'esterno |
| `govpay.logging.tracciatura.pattern-id-valido` | `GOVPAY_LOGGING_TRACCIATURA_PATTERNIDVALIDO` | `[A-Za-z0-9._:\-]+` | Espressione regolare che un identificativo ricevuto deve rispettare, altrimenti ne viene generato uno nuovo |
| `govpay.logging.tracciatura.correlation-inbound-headers` | `GOVPAY_LOGGING_TRACCIATURA_CORRELATIONINBOUNDHEADERS` | `X-Correlation-ID,X-Request-ID` | Header letti in ingresso per il correlation id, in ordine di precedenza |
| `govpay.logging.tracciatura.correlation-response-headers` | `GOVPAY_LOGGING_TRACCIATURA_CORRELATIONRESPONSEHEADERS` | `X-Correlation-ID,X-Request-ID` | Header di risposta con il correlation id |
| `govpay.logging.tracciatura.correlation-downstream-headers` | `GOVPAY_LOGGING_TRACCIATURA_CORRELATIONDOWNSTREAMHEADERS` | `X-Correlation-ID,X-Request-ID` | Header con il correlation id sulle chiamate in uscita |
| `govpay.logging.tracciatura.transaction-response-headers` | `GOVPAY_LOGGING_TRACCIATURA_TRANSACTIONRESPONSEHEADERS` | `X-Transaction-ID,X-Govpay-IdTransazione` | Header di risposta con il transaction id |

### Monitoraggio

| Proprietà | Variabile d'ambiente | Default | Descrizione |
|-----------|----------------------|---------|-------------|
| `management.server.port` | `MANAGEMENT_SERVER_PORT` | porta del servizio | Porta dedicata agli endpoint actuator |
| `management.endpoints.web.base-path` | `MANAGEMENT_ENDPOINTS_WEB_BASEPATH` | `/actuator` | Base path degli endpoint actuator |
| `management.endpoints.web.exposure.include` | `MANAGEMENT_ENDPOINTS` | `health,info,metrics,prometheus` | Endpoint actuator esposti |
| `management.endpoint.health.show-details` | `MANAGEMENT_ENDPOINT_HEALTH_SHOWDETAILS` | `when_authorized` | Dettagli dell'health: il servizio non ha autenticazione, quindi restituisce solo lo stato |
| `management.health.db.enabled` | `MANAGEMENT_HEALTH_DB_ENABLED` | `true` | Include la connessione al database nell'health |
| `management.metrics.tags.application` | `MANAGEMENT_METRICS_TAGS_APPLICATION` | `govpay-tracciati-batch` | Tag che identifica il servizio nelle metriche |
| `govpay.metrics.enabled` | `GOVPAY_METRICS_ENABLED` | `false` | Metriche comuni GovPay di `govpay-common` (tempi delle API e delle chiamate esterne) |

## Esecuzione

### Driver JDBC

I driver JDBC **non sono inclusi** nel fat jar e vanno forniti a runtime. Creare una directory (es. `jdbc-drivers/`) e copiarvi il driver del database utilizzato (versioni gestite da `govpay-bom`):

| Database   | Driver |
|------------|--------|
| PostgreSQL | [postgresql-42.7.14.jar](https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.14/postgresql-42.7.14.jar) |
| MySQL      | [mysql-connector-j-26.10.0.jar](https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/26.10.0/mysql-connector-j-26.10.0.jar) |
| Oracle     | [ojdbc11-23.26.3.0.0.jar](https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.26.3.0.0/ojdbc11-23.26.3.0.0.jar) |
| SQL Server | [mssql-jdbc-13.6.1.jre11.jar](https://repo1.maven.org/maven2/com/microsoft/sqlserver/mssql-jdbc/13.6.1.jre11/mssql-jdbc-13.6.1.jre11.jar) |
| H2         | [h2-2.5.252.jar](https://repo1.maven.org/maven2/com/h2database/h2/2.5.252/h2-2.5.252.jar) |

### Schema Spring Batch

Prima del primo avvio, nel database di GovPay devono esistere le tabelle dei metadati Spring Batch: si creano con lo script `sql/spring-batch/schema-<database>.sql` di `sql.zip`. L'immagine Docker lo esegue da sola se `GOVPAY_TRACCIATI_POP_DB_SKIP=FALSE`. Gli script in `sql/cleanup/` cancellano i metadati delle esecuzioni vecchie e vanno pianificati periodicamente: con un'esecuzione al minuto i metadati crescono in fretta.

### Jar standalone

Il jar usa `PropertiesLauncher`: la proprietà `loader.path` indica la directory dei driver JDBC. Configurazione minima per PostgreSQL, con lo scheduler interno:

```bash
java -Dloader.path=./jdbc-drivers \
     -Dspring.datasource.url=jdbc:postgresql://localhost:5432/govpay \
     -Dspring.datasource.username=govpay \
     -Dspring.datasource.password=<password> \
     -Dgovpay.tracciati.stampe.base-url=http://localhost:8081 \
     -jar target/govpay-tracciati-batch.jar
```

Modalità di esecuzione:

| Modalità | Come si attiva | Comportamento |
|----------|----------------|---------------|
| Scheduler interno | default (nessun profilo) | Il processo resta attivo, esegue il job ogni `scheduler.elaborazioneTracciatiPendenzeJob.fixedDelayString` ms ed espone API REST e actuator |
| Esecuzione singola | `--spring.profiles.active=cron` | Esegue il job una volta, quindi elabora al più un tracciato, e termina: la pianificazione è affidata a un cron esterno. L'exit code è `0` anche se il job fallisce o è già in esecuzione su un altro nodo, `1` solo se non si riesce a superare un'esecuzione bloccata: l'esito va letto nei log |

```bash
java -Dloader.path=./jdbc-drivers \
     -Dspring.datasource.url=jdbc:postgresql://localhost:5432/govpay \
     -Dspring.datasource.username=govpay \
     -Dspring.datasource.password=<password> \
     -jar target/govpay-tracciati-batch.jar --spring.profiles.active=cron
```

### War su application server

Il war si produce con `mvn clean install -P war` (`target/govpay-tracciati-batch.war`): non è allegato alle release né incluso nell'immagine Docker. Richiede un application server Jakarta EE 11 (Servlet 6.1), per esempio Tomcat 11.

Il war usa il datasource dell'application server, indicato con `spring.datasource.jndi-name`; il driver JDBC va nelle librerie dell'application server. Le altre proprietà si passano come proprietà di sistema della JVM o variabili d'ambiente. Esempio per Tomcat, con il datasource definito in `conf/context.xml`:

```xml
<Resource name="jdbc/govpay" auth="Container" type="javax.sql.DataSource"
          driverClassName="org.postgresql.Driver"
          url="jdbc:postgresql://localhost:5432/govpay"
          username="govpay" password="<password>" maxTotal="10"/>
```

```bash
export CATALINA_OPTS="-Dspring.datasource.jndi-name=java:comp/env/jdbc/govpay -Dgovpay.tracciati.stampe.base-url=http://localhost:8081"
cp target/govpay-tracciati-batch.war $CATALINA_BASE/webapps/
```

Il batch gira con lo scheduler interno; API REST e actuator rispondono sotto il context path del war (es. `http://localhost:8080/govpay-tracciati-batch/actuator/health`).

### Profilo di sviluppo

Il profilo `local` (`application-local.properties`) serve allo sviluppo: database PostgreSQL su `localhost`, nessun server HTTP, SQL e log di debug.

## Esecuzione con Docker

L'immagine di release è [linkitaly/govpay-tracciati-batch](https://hub.docker.com/r/linkitaly/govpay-tracciati-batch), pubblicata alla creazione di un tag di release (al momento nessuna). L'immagine di sviluppo `linkitaly/govpay-tracciati-batch-dev` è costruita a ogni push su `main`, con la versione del pom come tag (es. `1.0.0-SNAPSHOT`). Con lo scheduler interno il servizio risponde sulla porta `10001`; i driver JDBC si montano in `/opt/jdbc-drivers`.

```bash
docker run -d --name govpay-tracciati-batch -p 10001:10001 \
  -v ./jdbc-drivers:/opt/jdbc-drivers \
  -e GOVPAY_DB_TYPE=postgresql \
  -e GOVPAY_DB_SERVER=postgres:5432 \
  -e GOVPAY_DB_NAME=govpay \
  -e GOVPAY_DB_USER=govpay \
  -e GOVPAY_DB_PASSWORD=<password> \
  -e GOVPAY_TRACCIATI_STAMPE_BASEURL=http://govpay-stampe-api:8080 \
  linkitaly/govpay-tracciati-batch-dev:1.0.0-SNAPSHOT
```

Variabili d'ambiente dell'immagine, oltre alle proprietà Spring Boot in forma di variabile (es. `GOVPAY_BATCH_CARICAMENTOCHUNKSIZE=200`, `SCHEDULER_ELABORAZIONETRACCIATIPENDENZEJOB_FIXEDDELAYSTRING=300000`):

| Variabile | Obbligatoria | Default | Descrizione |
|-----------|--------------|---------|-------------|
| `GOVPAY_DB_TYPE` | sì | - | Tipo di database: `postgresql`, `mysql`, `mariadb`, `oracle` |
| `GOVPAY_DB_SERVER` | sì | - | Server del database, `host` o `host:porta` (porta di default del database se omessa) |
| `GOVPAY_DB_NAME` | sì | - | Nome del database (per Oracle, service name o SID) |
| `GOVPAY_DB_USER` | sì | - | Utente del database |
| `GOVPAY_DB_PASSWORD` | sì | - | Password del database (se manca l'avvio prosegue con un avviso) |
| `GOVPAY_DS_CONN_PARAM` | no | - | Parametri aggiunti all'URL JDBC (es. `zeroDateTimeBehavior=convertToNull` per MySQL) |
| `GOVPAY_DS_JDBC_LIBS` | no | `/opt/jdbc-drivers` | Directory dei driver JDBC |
| `GOVPAY_ORACLE_JDBC_URL_TYPE` | no | `servicename` | Formato dell'URL Oracle: `servicename` o `sid` |
| `ORACLE_TNS_ADMIN` | no | - | Directory dei file TNS di Oracle |
| `GOVPAY_TRACCIATI_MIN_POOL` | no | `2` | Connessioni minime inattive del pool |
| `GOVPAY_TRACCIATI_MAX_POOL` | no | `10` | Connessioni massime del pool |
| `GOVPAY_TRACCIATI_BATCH_USA_CRON` | no | - | `true`, `si`, `yes` o `1`: esecuzione singola con il profilo `cron`, senza server HTTP; il container termina a fine job e va pianificato dall'esterno (es. CronJob Kubernetes). Altrimenti scheduler interno |
| `GOVPAY_TRACCIATI_BATCH_INTERVALLO_CRON` | no | - | Minuti tra un'esecuzione e l'altra con lo scheduler interno; l'entrypoint li converte in millisecondi in `SCHEDULER_ELABORAZIONETRACCIATIPENDENZEJOB_FIXEDDELAYSTRING`. Deve essere un intero maggiore di zero, altrimenti il container non parte. Se non è impostata vale l'intervallo dell'applicazione, 60 secondi |
| `SCHEDULER_ELABORAZIONETRACCIATIPENDENZEJOB_FIXEDDELAYSTRING` | no | `60000` | Intervallo in millisecondi: se impostata ha la precedenza su `GOVPAY_TRACCIATI_BATCH_INTERVALLO_CRON`, che viene ignorata con un avviso |
| `SERVER_PORT` | no | `10001` | Porta HTTP con lo scheduler interno |
| `MANAGEMENT_SERVER_PORT` | no | porta del servizio | Porta dedicata agli endpoint actuator |
| `MANAGEMENT_ENDPOINTS` | no | `health,info,metrics,prometheus` | Endpoint actuator esposti |
| `LOG_LEVEL_REFRESH_INTERVAL` | no | `60s` | Intervallo di rilettura dei livelli di log dal database |
| `GOVPAY_TRACCIATI_POP_DB_SKIP` | no | `TRUE` | `FALSE` crea all'avvio le tabelle Spring Batch, se la tabella `GOVPAY_TRACCIATI_DB_CHECK_TABLE` non esiste |
| `GOVPAY_TRACCIATI_DB_CHECK_TABLE` | no | `batch_job_execution_context` | Tabella che indica che lo schema Spring Batch è già presente |
| `GOVPAY_TRACCIATI_LIVE_DB_CHECK_SKIP` | no | `FALSE` | Salta l'attesa della porta del database prima dell'inizializzazione |
| `GOVPAY_TRACCIATI_LIVE_DB_CHECK_MAX_RETRY` | no | `30` | Tentativi di connessione alla porta del database |
| `GOVPAY_TRACCIATI_LIVE_DB_CHECK_SLEEP_TIME` | no | `2` | Secondi tra un tentativo e l'altro |
| `GOVPAY_TRACCIATI_LIVE_DB_CHECK_CONNECT_TIMEOUT` | no | `5` | Timeout di ogni tentativo di connessione, in secondi |
| `GOVPAY_TRACCIATI_READY_DB_CHECK_SKIP` | no | `FALSE` | Salta la verifica della tabella e crea comunque lo schema |
| `GOVPAY_TRACCIATI_READY_DB_CHECK_MAX_RETRY` | no | `5` | Tentativi della verifica della tabella |
| `GOVPAY_TRACCIATI_READY_DB_CHECK_SLEEP_TIME` | no | `2` | Secondi tra un tentativo e l'altro |
| `GOVPAY_TRACCIATI_JVM_MAX_RAM_PERCENTAGE` | no | `80` | `-XX:MaxRAMPercentage` della JVM |
| `GOVPAY_TRACCIATI_JVM_INITIAL_RAM_PERCENTAGE` | no | - | `-XX:InitialRAMPercentage` della JVM |
| `GOVPAY_TRACCIATI_JVM_MIN_RAM_PERCENTAGE` | no | - | `-XX:MinRAMPercentage` della JVM |
| `GOVPAY_TRACCIATI_JVM_MAX_METASPACE_SIZE` | no | - | `-XX:MaxMetaspaceSize` della JVM |
| `GOVPAY_TRACCIATI_JVM_MAX_DIRECT_MEMORY_SIZE` | no | - | `-XX:MaxDirectMemorySize` della JVM |
| `JAVA_OPTS` | no | - | Opzioni aggiuntive della JVM |

L'identificativo del nodo (`govpay.batch.cluster-id`) è impostato dall'entrypoint, con la variabile `GOVPAY_BATCH_CLUSTER_ID`, all'indirizzo IP del container o al suo hostname se l'IP non si ricava: un valore passato dall'esterno viene sovrascritto. Le altre variabili che compaiono negli script dell'immagine (es. `INTERVALLO_MINUTI`, `HSQLDB_SHA256`) sono interne e non vanno impostate.

Il Dockerfile imposta anche, con `ENV`, i valori del container indicati in [Configurazione](#configurazione) (pool Hikari, file di log). I messaggi dell'entrypoint e i log dell'applicazione escono sui log del container (`docker logs`); il log applicativo è scritto anche in `/var/log/govpay-tracciati/govpay-tracciati.log`.

Costruzione dell'immagine in locale, scansione delle vulnerabilità e pacchetti inclusi sono descritti in [docker/DOCKER.md](docker/DOCKER.md) e [docker/SCANSIONE.md](docker/SCANSIONE.md).

## Monitoraggio

Con lo scheduler interno gli endpoint actuator rispondono di default sulla porta del servizio (`8080` con il jar, `10001` nel container; con il war, sotto il context path dell'applicazione):

```bash
curl http://localhost:10001/actuator/health       # stato, compresa la connessione al database
curl http://localhost:10001/actuator/prometheus   # metriche in formato Prometheus
```

Oltre alle metriche standard, il batch espone i contatori `govpay_tracciati_completati_total` (tracciati elaborati con successo) e `govpay_tracciati_scartati_total` (tracciati scartati per errore).

Per spostare gli endpoint su una porta dedicata impostare `management.server.port` (nel container `MANAGEMENT_SERVER_PORT`). L'endpoint `loggers`, che permette di cambiare i livelli di log a caldo, non è esposto di default perché il servizio non ha autenticazione: abilitarlo solo insieme a una porta dedicata e non raggiungibile dall'esterno, per esempio `MANAGEMENT_SERVER_PORT=9090` e `MANAGEMENT_ENDPOINTS=health,info,metrics,prometheus,loggers`. In alternativa i livelli si cambiano dalla console, tramite la tabella `configurazione`.

Con il profilo `cron` non c'è server HTTP nel container: l'esito si ricava dall'exit code del processo e dai log.

## Avvio manuale via REST

Con lo scheduler interno il batch espone, sotto `/api/batch`, l'API comune ai batch GovPay (`AbstractBatchController` di `govpay-common`). Non ha autenticazione: va esposta solo sulla rete interna.

| Metodo e path | Descrizione |
|---------------|-------------|
| `GET /api/batch/run` | Avvia il job in modo asincrono (`202`, oppure `409` se è già in esecuzione); elabora il prossimo tracciato in attesa |
| `GET /api/batch/run?force=true` | Avvia il job anche se risulta un'esecuzione in corso |
| `GET /api/batch/status` | Stato corrente del job |
| `GET /api/batch/lastExecution` | Ultima esecuzione completata |
| `GET /api/batch/nextExecution` | Prossima esecuzione stimata |
| `GET /api/batch/info` | Nome e descrizione del batch |
| `GET /api/batch/executions` | Elenco paginato delle esecuzioni |
| `GET /api/batch/executions/{executionId}` | Dettaglio di un'esecuzione |
| `DELETE /api/batch/executions/{executionId}` | Richiede l'arresto di un'esecuzione (`409` se è già terminata) |
| `GET /api/batch/cache/clear` | Nessun effetto: il batch non ha cache da svuotare |

```bash
curl http://localhost:10001/api/batch/run
curl http://localhost:10001/api/batch/status
```

## Elaborazione

Il job `elaborazioneTracciatiPendenzeJob` ha cinque step, ciascuno con una guardia sullo stato del tracciato: un'esecuzione interrotta riprende dal punto in cui il tracciato è rimasto (`ELABORAZIONE` o `IN_STAMPA`).

| Step | Quando | Cosa fa |
|------|--------|---------|
| `caricamentoPendenzeStep` | stato `ELABORAZIONE` | Legge le righe da `raw_richiesta` (JSON: inserimenti e annullamenti), le divide in lotti di `caricamento-chunk-size` righe elaborati in parallelo, carica o annulla ogni pendenza e registra una riga in `operazioni` per linea (upsert su tracciato e linea, quindi idempotente in ripresa) |
| `produzioneEsitoStep` | stato `ELABORAZIONE` | Produce l'esito dalle `operazioni` e lo salva in `raw_esito`, con nome `esito_<file della richiesta>` |
| `completaCaricamentoStep` | stato `ELABORAZIONE` | Imposta `CARICAMENTO_OK` o `CARICAMENTO_KO` nel `bean_dati`; il tracciato passa a `IN_STAMPA` se è richiesta la stampa degli avvisi, altrimenti a `COMPLETATO` |
| `stampaAvvisiStep` | stato `IN_STAMPA` e stampa richiesta | Chiede a govpay-stampe-api il PDF di ogni pendenza caricata con numero avviso, in lotti paralleli di `stampe-chunk-size`; salva ogni PDF in `stampe` e scrive lo ZIP di tutti gli avvisi in `tracciati.zip_stampe` in streaming. Una stampa fallita è contata come KO e non interrompe lo step |
| `finalizzazioneStep` | stato `IN_STAMPA` | Porta il tracciato a `COMPLETATO` |

A fine elaborazione il batch incrementa la metrica `govpay.tracciati.completati` e invia l'evento `OK` al GDE. Se un'esecuzione fallisce, il tracciato viene scartato come nella procedura del monolite: stato `SCARTATO`, causa dell'errore in `descrizione_stato` e nel `bean_dati`, metrica `govpay.tracciati.scartati` ed evento `KO` al GDE; le esecuzioni successive passano al tracciato seguente. Se non si riesce nemmeno a scartarlo, per esempio perché il database non è raggiungibile, il tracciato resta nello stato in cui si trova e viene ripreso all'esecuzione successiva. Un'esecuzione interrotta senza errore (arresto del processo, esecuzione bloccata superata da un altro nodo) riprende invece dal punto in cui era rimasta.

## Licenza

GovPay Tracciati Batch è distribuito con licenza [GNU General Public License v3.0](LICENSE).

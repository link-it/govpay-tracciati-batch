# GovPay Tracciati - Configurazione Docker

Containerizzazione Docker per il batch GovPay Tracciati che gestisce il caricamento massivo di posizioni debitorie in GovPay a partire dai tracciati di caricamento.

## Panoramica

Questa configurazione Docker fornisce:
- Supporto multi-database (PostgreSQL, MySQL/MariaDB, Oracle)
- Modalità di deployment flessibili (schedulato da orchestratore o modalita cron)
- Elaborazione dei tracciati di caricamento delle posizioni debitorie
- Integrazione opzionale con GDE (Giornale degli Eventi)
- Health check e monitoraggio
- Connection pooling configurabile

## Avvio Rapido

### 1. Costruire l'Immagine Docker

```bash
# Build con supporto PostgreSQL (predefinito)
./build_image.sh
```

### 2. Configurazione obbligatoria
- `GOVPAY_DB_TYPE`: Tipo database (postgresql, mysql, mariadb, oracle)
- `GOVPAY_DB_SERVER`: Server database (formato: host:porta)
- `GOVPAY_DB_NAME`: Nome del database
- `GOVPAY_DB_USER`: Username del database
- `GOVPAY_DB_PASSWORD`: Password del database

**Nota:** La configurazione applicativa del batch (parametri di elaborazione dei tracciati, connettori verso i servizi GovPay) viene gestita tramite le tabelle di configurazione del database GovPay, utilizzando la libreria `govpay-common`.

### 3. Avviare i Servizi

```bash
# Avvio con docker-compose
docker compose up -d

# Visualizzare i log
docker compose logs -f govpay-tracciati

# Verificare lo stato
docker compose ps
```

## Architettura

```
┌─────────────────────────────────────┐
│   GovPay Tracciati Batch Processor        │
│                                     │
│  ┌──────────────────────────────┐  │
│  │   Applicazione Spring Boot   │  │
│  │   - Spring Batch             │  │
│  │   - Elaborazione tracciati   │  │
│  │   - Client GDE (opzionale)   │  │
│  └──────────────────────────────┘  │
│              │                      │
│              ▼                      │
│  ┌──────────────────────────────┐  │
│  │   HikariCP Connection Pool   │  │
│  └──────────────────────────────┘  │
└─────────────────┬───────────────────┘
                  │
                  ▼
      ┌───────────────────────┐
      │   Database (RDBMS)    │
      │  - PostgreSQL         │
      │  - MySQL/MariaDB      │
      │  - Oracle             │
      └───────────────────────┘
```

## Modalità di Deployment

### Modalità CRON (Auto-schedulata)

Esecuzione continua con job batch auto-schedulati tramite Spring Scheduler:

```env
GOVPAY_TRACCIATI_BATCH_USA_CRON=true
GOVPAY_TRACCIATI_BATCH_INTERVALLO_CRON=5  # Intervallo in minuti (default: 5)
SERVER_PORT=10001  # Porta per Actuator (default: 10001)
```

**Caratteristiche:**
- Il container rimane attivo come daemon
- Scheduler interno esegue il batch ad intervalli regolari
- Espone endpoint Spring Boot Actuator per health check e metriche
- Ideale per deployment con orchestratori (Kubernetes, Docker Swarm, systemd)

### Modalità GESTITO (Schedulazione Esterna)

Esegue il batch una volta ed esce (per schedulazione esterna tramite cron, Kubernetes CronJob, ecc.):

```env
GOVPAY_TRACCIATI_BATCH_USA_CRON=false  # o non impostare la variabile
```

**Caratteristiche:**
- Il container esegue il batch ed esce immediatamente
- Non espone endpoint Actuator
- La schedulazione è gestita esternamente (cron, orchestratore)
- Ideale per CronJob di Kubernetes o cron di sistema

Esempio di configurazione cron:
```bash
# Esecuzione ogni ora
0 * * * * docker run --rm --env-file .env govpay-tracciati-batch:latest
```

Esempio CronJob Kubernetes:
```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: govpay-tracciati-batch
spec:
  schedule: "0 * * * *"  # Ogni ora
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: govpay-tracciati
            image: govpay-tracciati-batch:latest
            envFrom:
            - configMapRef:
                name: govpay-tracciati-config
          restartPolicy: OnFailure
```

## Configurazione Database

Il container supporta la nomenclatura delle variabili compatibile con govpay-docker. L'URL JDBC viene costruito automaticamente in base al tipo di database.

### PostgreSQL (Default)

```env
GOVPAY_DB_TYPE=postgresql
GOVPAY_DB_SERVER=postgres:5432
GOVPAY_DB_NAME=govpay
GOVPAY_DB_USER=govpay
GOVPAY_DB_PASSWORD=yourpassword
```

**Parametri opzionali:**
```env
GOVPAY_DS_CONN_PARAM=sslmode=require&connectTimeout=10  # Parametri JDBC aggiuntivi
GOVPAY_TRACCIATI_MIN_POOL=2  # Connessioni minime nel pool (default: 2)
GOVPAY_TRACCIATI_MAX_POOL=10  # Connessioni massime nel pool (default: 10)
```

### MySQL/MariaDB

```env
GOVPAY_DB_TYPE=mysql  # oppure mariadb
GOVPAY_DB_SERVER=mysql:3306
GOVPAY_DB_NAME=govpay
GOVPAY_DB_USER=govpay
GOVPAY_DB_PASSWORD=yourpassword
```

**Parametri opzionali:**
```env
GOVPAY_DS_CONN_PARAM=zeroDateTimeBehavior=convertToNull&useSSL=false
```

### Oracle

```env
GOVPAY_DB_TYPE=oracle
GOVPAY_DB_SERVER=oracle:1521
GOVPAY_DB_NAME=XE  # Service name o SID
GOVPAY_DB_USER=govpay
GOVPAY_DB_PASSWORD=yourpassword
```

**Configurazione Service Name (default):**
```env
GOVPAY_ORACLE_JDBC_URL_TYPE=servicename  # Genera: jdbc:oracle:thin:@//host:port/service
```

**Configurazione SID:**
```env
GOVPAY_ORACLE_JDBC_URL_TYPE=sid  # Genera: jdbc:oracle:thin:@host:port:sid
```

**Nota:** Il formato con TNS Names non è attualmente supportato dalla configurazione automatica. Per utilizzare TNS Names, è necessario costruire manualmente l'URL JDBC.

### Inizializzazione Database Automatica

Il container può inizializzare automaticamente le tabelle del batch se non esistono:

```env
GOVPAY_TRACCIATI_POP_DB_SKIP=FALSE  # Abilita inizializzazione (default: TRUE)
GOVPAY_TRACCIATI_DB_CHECK_TABLE=batch_job_execution_context  # Tabella di riferimento
```

**Configurazione health check:**
```env
# Liveness check (connessione TCP al DB)
GOVPAY_TRACCIATI_LIVE_DB_CHECK_SKIP=FALSE
GOVPAY_TRACCIATI_LIVE_DB_CHECK_MAX_RETRY=30
GOVPAY_TRACCIATI_LIVE_DB_CHECK_SLEEP_TIME=2
GOVPAY_TRACCIATI_LIVE_DB_CHECK_CONNECT_TIMEOUT=5

# Readiness check (verifica esistenza tabelle)
GOVPAY_TRACCIATI_READY_DB_CHECK_SKIP=FALSE
GOVPAY_TRACCIATI_READY_DB_CHECK_MAX_RETRY=5
GOVPAY_TRACCIATI_READY_DB_CHECK_SLEEP_TIME=2
```

## Integrazione con GDE (Giornale degli Eventi)

L'integrazione con GDE (Giornale degli Eventi) viene configurata tramite la tabella `CONFIGURAZIONE` del database GovPay.
Non sono necessarie variabili d'ambiente per abilitare o configurare il GDE.

## Configuration Reference

L'elenco completo delle variabili d'ambiente dell'immagine, con default e descrizione, è nel README del progetto: [Esecuzione con Docker](../README.md#esecuzione-con-docker). Le proprietà applicative sono descritte in [Configurazione](../README.md#configurazione).

### Health Check

In modalità CRON, il container espone gli endpoint Spring Boot Actuator:

```bash
# Health check
curl http://localhost:10001/actuator/health

# Verifica dettagliata
curl http://localhost:10001/actuator/health/liveness
curl http://localhost:10001/actuator/health/readiness
```

**Nota:** La porta di default è `10001` (configurabile tramite `SERVER_PORT`)

### Logs

```bash
# Seguire i log del container
docker-compose logs -f govpay-tracciati

# Log dall'interno del container
docker exec -it govpay-tracciati-batch tail -f /var/log/govpay/govpay-tracciati.log

# Debug dell'entrypoint (per troubleshooting avvio)
docker exec -it govpay-tracciati-batch cat /tmp/entrypoint_debug.log
```

### Metrics

Accesso alle metriche Spring Boot (solo modalità CRON):
```bash
# Metriche generali
curl http://localhost:10001/actuator/metrics

# Metriche specifiche
curl http://localhost:10001/actuator/metrics/jvm.memory.used
curl http://localhost:10001/actuator/metrics/hikaricp.connections.active
```

## File Structure

```
govpay-tracciati-batch/docker/
├── govpay-tracciati
|   └── Dockerfile.github          # Definizione immagine Docker
├── build_image.sh                 # Script di build
├── DOCKER.md                      # Questa documentazione
└── commons/
    ├── entrypoint.sh             # Script di avvio container
    └── init_tracciati_db.sh            # Script inizializzazione Database
```

## Esempi di Configurazione

### Esempio Minimo (PostgreSQL + UAT)

```env
# Database
GOVPAY_DB_TYPE=postgresql
GOVPAY_DB_SERVER=postgres:5432
GOVPAY_DB_NAME=govpay
GOVPAY_DB_USER=govpay
GOVPAY_DB_PASSWORD=secret123

# Modalità auto-schedulata (ogni 5 minuti)
GOVPAY_TRACCIATI_BATCH_USA_CRON=true
```

**Nota:** La configurazione applicativa del batch viene gestita nelle tabelle di configurazione del database GovPay.

### Esempio Completo (Oracle + Produzione + Inizializzazione DB)

```env
# Database Oracle
GOVPAY_DB_TYPE=oracle
GOVPAY_DB_SERVER=oracle-prod.example.com:1521
GOVPAY_DB_NAME=GOVPAYDB
GOVPAY_DB_USER=govpay_tracciati
GOVPAY_DB_PASSWORD=SecurePassword123!
GOVPAY_ORACLE_JDBC_URL_TYPE=servicename
GOVPAY_DS_CONN_PARAM=oracle.net.ssl_version=1.2

# Pool connessioni
GOVPAY_TRACCIATI_MIN_POOL=5
GOVPAY_TRACCIATI_MAX_POOL=20

# Inizializzazione database
GOVPAY_TRACCIATI_POP_DB_SKIP=FALSE
GOVPAY_TRACCIATI_LIVE_DB_CHECK_MAX_RETRY=60

# Modalità gestita esternamente (CronJob K8s)
GOVPAY_TRACCIATI_BATCH_USA_CRON=false

# JVM Memory
GOVPAY_TRACCIATI_JVM_MAX_RAM_PERCENTAGE=75
GOVPAY_TRACCIATI_JVM_MAX_METASPACE_SIZE=256m
```

## Supporto

Per problemi relativi a:
- **GovPay**: https://github.com/link-it/govpay
- **pagoPA GPD**: https://docs.pagopa.it/
- **Sviluppo Java**: Consultare `README.md`

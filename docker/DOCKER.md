# GovPay Tracciati Batch - Immagine Docker

Immagine del batch che elabora i tracciati di caricamento massivo delle pendenze di GovPay (`elaborazioneTracciatiPendenzeJob`). Cosa fa il batch, come si configura e l'elenco completo delle variabili d'ambiente sono nel [README](../README.md#esecuzione-con-docker): qui solo ciò che riguarda l'immagine.

## Contenuto

| Percorso | Contenuto |
|----------|-----------|
| `/opt/govpay-tracciati/govpay-tracciati-batch.jar` | Fat jar del batch |
| `/opt/jdbc-drivers` | Driver JDBC, da montare: non sono inclusi nell'immagine (vedi [jdbc-drivers/README.md](jdbc-drivers/README.md)) |
| `/opt/sql` | Script SQL di `sql.zip`: schema Spring Batch per database (`spring-batch/`) e svecchiamento dei metadati (`cleanup/`) |
| `/opt/hsqldb-<versione>` | SqlTool di HSQLDB, usato per creare lo schema Spring Batch all'avvio |
| `/var/log/govpay-tracciati/govpay-tracciati.log` | File di log dell'applicazione |

Il processo gira con l'utente `govpay`; l'entrypoint è `/usr/local/bin/entrypoint.sh`. I messaggi dell'entrypoint e i log dell'applicazione escono anche sui log del container (`docker logs`).

## Costruzione

```bash
cd docker
./build_image.sh                       # ultima release pubblicata su GitHub
./build_image.sh -v <versione>         # una release specifica
./build_image.sh -l ../target/govpay-tracciati-batch.jar -t govpay-tracciati-batch:locale   # jar compilato in locale
```

Con una release, jar e `sql.zip` sono scaricati da GitHub e verificati con lo sha256 letto dalle API delle release (`-s` e `-q` per indicarlo a mano). Finché non c'è una release pubblicata si usa `-l`: in questo caso si usa `Dockerfile.daFile` e gli script SQL vengono da `../target/sql.zip`, prodotto da `mvn clean install -Pjar,dist`; senza, l'immagine non ha gli script per creare lo schema. `./build_image.sh -h` elenca tutte le opzioni.

## Modalità di esecuzione

| Modalità | Come si attiva | Comportamento |
|----------|----------------|---------------|
| Scheduler interno | default (`GOVPAY_TRACCIATI_BATCH_USA_CRON` non impostata o falsa) | Il container resta attivo ed esegue il job a intervalli, un tracciato per esecuzione; espone API REST e actuator sulla porta `10001` |
| Esecuzione singola | `GOVPAY_TRACCIATI_BATCH_USA_CRON=true` (anche `si`, `yes`, `1`) | Profilo `cron`: esegue il job una volta, quindi elabora al più un tracciato, e termina, senza server HTTP. La pianificazione è esterna (cron di sistema, CronJob Kubernetes) |

Con lo scheduler interno l'intervallo è di 60 secondi. Si cambia in minuti con `GOVPAY_TRACCIATI_BATCH_INTERVALLO_CRON`, che l'entrypoint converte nella proprietà `scheduler.elaborazioneTracciatiPendenzeJob.fixedDelayString`, oppure in millisecondi con `SCHEDULER_ELABORAZIONETRACCIATIPENDENZEJOB_FIXEDDELAYSTRING`, che ha la precedenza. Un valore di `GOVPAY_TRACCIATI_BATCH_INTERVALLO_CRON` che non sia un intero maggiore di zero blocca l'avvio. Il valore usato è scritto nel log di avvio:

```
[INFO] ... - Porta HTTP: 10001, Intervallo scheduler: 5 minuti (300000ms)
```

In esecuzione singola l'exit code è `0` anche quando il job fallisce: l'esito va letto nei log.

Esempio di CronJob Kubernetes per l'esecuzione singola:

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: govpay-tracciati-batch
spec:
  schedule: "*/5 * * * *"
  concurrencyPolicy: Forbid
  jobTemplate:
    spec:
      template:
        spec:
          restartPolicy: Never
          containers:
          - name: govpay-tracciati-batch
            image: linkitaly/govpay-tracciati-batch:<versione>
            env:
            - name: GOVPAY_TRACCIATI_BATCH_USA_CRON
              value: "true"
            envFrom:
            - configMapRef:
                name: govpay-tracciati-config
            - secretRef:
                name: govpay-tracciati-db
            volumeMounts:
            - name: jdbc-drivers
              mountPath: /opt/jdbc-drivers
          volumes:
          - name: jdbc-drivers
            persistentVolumeClaim:
              claimName: govpay-jdbc-drivers
```

## Database

L'URL JDBC è costruito dall'entrypoint con `GOVPAY_DB_TYPE` (`postgresql`, `mysql`, `mariadb`, `oracle`), `GOVPAY_DB_SERVER`, `GOVPAY_DB_NAME` e i parametri opzionali `GOVPAY_DS_CONN_PARAM` e, per Oracle, `GOVPAY_ORACLE_JDBC_URL_TYPE` (`servicename` o `sid`). La password si passa con `GOVPAY_DB_PASSWORD`, preferibilmente da un secret; l'entrypoint non la scrive nei log.

## Schema Spring Batch

Con `GOVPAY_TRACCIATI_POP_DB_SKIP=FALSE` l'entrypoint attende il database, controlla se esiste la tabella `GOVPAY_TRACCIATI_DB_CHECK_TABLE` (default `batch_job_execution_context`) e, se manca, crea le tabelle dei metadati Spring Batch con lo script `/opt/sql/spring-batch/schema-<database>.sql`. Le tabelle di GovPay (`tracciati`, `operazioni`, `versamenti`, ...) non vengono create: sono degli script di installazione di GovPay. Se l'inizializzazione fallisce il container termina con un errore.

## Giornale degli Eventi e stampe

L'invio degli eventi al GDE non si configura con variabili del container ma con il connettore `govpay_gde_api` di GovPay (tabella `connettori`). L'URL di govpay-stampe-api, necessario per i tracciati con la stampa degli avvisi, si passa con `GOVPAY_TRACCIATI_STAMPE_BASEURL`.

## Scansione delle vulnerabilità

Le immagini pubblicate dalla pipeline sono scansionate prima della pubblicazione: vedi [SCANSIONE.md](SCANSIONE.md).

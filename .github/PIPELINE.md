# Pipeline di Validazione CI/CD

Questo progetto utilizza GitHub Actions per la validazione automatica del codice e la creazione di release.

## Workflow Maven

Il workflow principale (`.github/workflows/maven.yml`) esegue i seguenti passaggi:

### Job: Build

1. **Setup Ambiente**
   - Checkout del codice
   - Configurazione timezone Europe/Rome
   - Setup JDK 21 (Temurin distribution)
   - Cache delle dipendenze Maven

2. **Security Scanning**
   - Cache del database OWASP Dependency-Check
   - Verifica delle vulnerabilità note (NVD)
   - Generazione report di sicurezza (HTML e XML)

3. **Build e Test**
   - Compilazione del progetto: `mvn clean install`
   - Esecuzione dei test unitari
   - Generazione report di code coverage con JaCoCo

4. **Code Quality Analysis**
   - Scansione SonarCloud per analisi statica del codice
   - Verifica qualità, bugs, vulnerabilità, code smells
   - Report di copertura del codice

5. **License Analysis**
   - Download delle informazioni sulle licenze delle dipendenze
   - Analisi automatica della compatibilità delle licenze
   - Validazione contro le licenze approvate
   - Generazione report dettagliato in formato CSV e JSON
   - Gestione eccezioni tramite file di configurazione

6. **Artifact Upload**
   - JAR dell'applicazione
   - Report JaCoCo (XML e ZIP HTML)
   - Report OWASP Dependency-Check (HTML e XML)
   - Report License Analysis (third-party-licenses/)

### Job: OSV Scan

Scansione delle vulnerabilità delle dipendenze tramite Google OSV Scanner (eseguito su push su `main` e sui tag). Il lockfile analizzato è `pom.xml`.

### Job: SBOM

Generazione della Software Bill of Materials in formato CycloneDX (json + xml) tramite `cyclonedx-maven-plugin` (su push su `main` e sui tag).

### Job: Release

Eseguito solo quando viene creato un tag Git:

1. **Preparazione Artifact**
   - Download degli artifact dai job build / osv-scan / sbom
   - Rinomina del JAR con il numero di versione (tag)
   - Creazione ZIP con gli script SQL

2. **GitHub Release**
   - Creazione automatica della release su GitHub
   - Upload dei seguenti file:
     - `govpay-tracciati-batch-{version}.jar`
     - `sql.zip` (script SQL per tutti i database)
     - `release-reports-{version}.zip` (OWASP, JaCoCo, OSV, SBOM, licenze)

### Job: Docker

Eseguito solo sui tag, dopo il job release: build e push dell'immagine Docker su Docker Hub
(`linkitaly/govpay-tracciati-batch:{version}` e `:latest`).

> Nota: il job Docker richiede la presenza della cartella `docker/` con il contesto di build
> (`docker/commons`) e il Dockerfile `docker/govpay-tracciati/Dockerfile.github`. Vanno creati
> prima del primo tag. Analogamente il job Release si aspetta gli script SQL in `src/main/resources/sql`.

## Secrets e Variables Richiesti

Configura i seguenti secrets/variables nel repository GitHub (Settings → Secrets and variables → Actions):

| Nome | Tipo | Descrizione | Obbligatorio |
|------|------|-------------|--------------|
| `NVD_API_KEY` | secret | API Key per il National Vulnerability Database | Consigliato |
| `SONAR_TOKEN` | secret | Token di autenticazione SonarCloud | Sì |
| `GH_TOKEN` | secret | GitHub Personal Access Token per la creazione della release | Sì (per i tag) |
| `OSS_INDEX_USER` | variable | Utente OSS Index (opzionale) | No |
| `OSS_INDEX_PASSWORD` | secret | Password OSS Index (opzionale) | No |
| `DOCKERHUB_USERNAME` | variable | Utente Docker Hub | Sì (per i tag) |
| `DOCKERHUB_TOKEN` | secret | Token Docker Hub | Sì (per i tag) |

### Come ottenere i secrets:

**NVD_API_KEY:**
1. Registrati su https://nvd.nist.gov/developers/request-an-api-key
2. Riceverai la chiave via email
3. Aggiungi come secret nel repository GitHub

**SONAR_TOKEN:**
1. Accedi a SonarCloud (https://sonarcloud.io)
2. Vai su Account → Security
3. Genera un nuovo token
4. Aggiungi come secret nel repository GitHub

**GH_TOKEN:**
1. Vai su GitHub Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Genera un nuovo token con i permessi:
   - `repo` (full control)
   - `write:packages`
3. Aggiungi come secret nel repository GitHub

## SonarCloud Setup

1. Importa il progetto su SonarCloud
2. Configura l'organizzazione: `link-it`
3. Verifica che il projectKey sia: `link-it_govpay-tracciati-batch`
4. Il file `sonar-project.properties` contiene la configurazione

## Trigger della Pipeline

### Push su main
```bash
git push origin main
```
Esegue: Build, test, security scan, code quality analysis, OSV scan, SBOM

### Pull Request su main
Esegue: Build, test, security scan, code quality analysis

### Creazione Tag (Release)
```bash
# Crea un tag per la versione
git tag 1.0.0
git push origin 1.0.0
```
Esegue: Build completo + release GitHub con tutti gli artifact + build/push immagine Docker

## Plugin Maven Configurati

### JaCoCo (Code Coverage)
- **Report:** `target/site/jacoco/`
- **Esclusi:** Test classes e package di test
- **Comando locale:** `mvn verify` (genera il report)

### OWASP Dependency-Check
- **Report:** `target/dependency-check-report.html`
- **Fase:** verify
- **Comando locale:** `mvn verify` (esegue il check)
- **Cache:** `.dependency-check/data` (esclusa da Git)

## Refresh OWASP DB

Il workflow `.github/workflows/refresh-owasp-db.yml` aggiorna quotidianamente (cron 03:00) la cache
del database NVD usata da OWASP Dependency-Check, per rendere più veloci e stabili le build. Può essere
lanciato anche manualmente (`workflow_dispatch`).

## Esecuzione Locale

### Build completo con tutti i check
```bash
mvn clean install
```

### Solo build e test (senza security check)
```bash
mvn clean install -Dowasp.phase=none
```

### Solo security check
```bash
mvn dependency-check:aggregate
```

### Solo code coverage
```bash
mvn clean test jacoco:report
```

## License Analysis

L'analisi delle licenze verifica automaticamente la compatibilità delle dipendenze con i requisiti di licenza del progetto.

### Funzionalità

Lo script Python (`.github/workflows/scripts/analyze_licenses.py`) analizza:
- **Compatibilità GPLv3**: Verifica che tutte le licenze siano compatibili con GPLv3
- **Enterprise Safety**: Identifica licenze problematiche per uso enterprise
- **Licenze Sconosciute**: Segnala dipendenze senza licenza o con licenze non riconosciute
- **Report Dettagliati**: Genera CSV e JSON con dettagli completi

### File di Eccezioni

Le eccezioni sono configurate in `.github/workflows/scripts/license-exceptions.json`:

```json
{
  "exceptions": [
    {
      "groupId": "org.example",
      "artifactId": "some-artifact",
      "reason": "Spiegazione del motivo dell'eccezione",
      "exclude_from_reports": true
    }
  ]
}
```

Campi disponibili:
- `groupId`: Maven groupId (supporta wildcard `*`)
- `artifactId`: Maven artifactId (supporta wildcard `*`)
- `reason`: Motivazione dell'eccezione (obbligatorio)
- `exclude_from_reports`: Se `true`, escluso completamente dai report

### Esecuzione Locale

```bash
# Download informazioni licenze
mvn org.codehaus.mojo:license-maven-plugin:2.4.0:aggregate-download-licenses \
  -DexcludeScopes=test,provided,system \
  -DincludeTransitiveDependencies=true

# Analisi licenze
python3 .github/workflows/scripts/analyze_licenses.py \
  --exceptions .github/workflows/scripts/license-exceptions.json
```

## Troubleshooting

### Build fallisce per timeout OWASP
- Aumentare `nvdApiDelay` nel pom.xml
- Verificare la connessione internet
- Usare `NVD_API_KEY` per aumentare il rate limit

### SonarCloud non riceve i dati
- Verificare che `SONAR_TOKEN` sia configurato correttamente
- Controllare che l'organizzazione e projectKey siano corretti
- Verificare che il report JaCoCo sia generato: `target/site/jacoco/jacoco.xml`

### Release non viene creata
- Verificare che `GH_TOKEN` abbia i permessi corretti
- Il workflow release si attiva solo con i tag: `git push origin <tag>`
- Verificare i log del job release in GitHub Actions

## Badge per README

Aggiungi questi badge al tuo README.md:

```markdown
[![Java CI with Maven](https://github.com/link-it/govpay-tracciati-batch/actions/workflows/maven.yml/badge.svg)](https://github.com/link-it/govpay-tracciati-batch/actions/workflows/maven.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=link-it_govpay-tracciati-batch&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=link-it_govpay-tracciati-batch)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=link-it_govpay-tracciati-batch&metric=coverage)](https://sonarcloud.io/summary/new_code?id=link-it_govpay-tracciati-batch)
```

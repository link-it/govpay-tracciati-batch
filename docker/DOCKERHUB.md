<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay Tracciati Batch

[![GitHub](https://img.shields.io/badge/GitHub-link--it%2Fgovpay--tracciati--batch-blue?logo=github)](https://github.com/link-it/govpay-tracciati-batch)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

Batch Spring Boot per il **caricamento massivo di posizioni debitorie** in GovPay.

## Cos'è GovPay Tracciati Batch

GovPay Tracciati Batch è un componente del progetto [GovPay](https://github.com/link-it/govpay) che si occupa del caricamento massivo delle posizioni debitorie a partire dai tracciati di caricamento.

### Funzionalità principali

- Caricamento massivo di posizioni debitorie in GovPay
- Supporto multi-database: PostgreSQL, MySQL/MariaDB, Oracle
- Modalità di deployment flessibili (daemon o esecuzione singola)
- Integrazione opzionale con GDE (Giornale degli Eventi)
- Health check e monitoraggio tramite Spring Boot Actuator
- Gestione automatica del recovery per job bloccati

## Versioni disponibili

- `latest` - ultima versione stabile

Ogni release pubblicata su GitHub genera il tag corrispondente dell'immagine.
Storico completo delle modifiche consultabile nel [ChangeLog](https://github.com/link-it/govpay-tracciati-batch/blob/main/ChangeLog) del progetto.

## Quick Start

```bash
docker pull linkitaly/govpay-tracciati-batch:latest
```

## Documentazione

- [README e istruzioni di configurazione](https://github.com/link-it/govpay-tracciati-batch/blob/main/README.md)
- [Documentazione Docker](https://github.com/link-it/govpay-tracciati-batch/blob/main/docker/DOCKER.md)
- [Dockerfile](https://github.com/link-it/govpay-tracciati-batch/blob/main/docker/govpay-tracciati/Dockerfile.github)

## Licenza

GovPay Tracciati Batch è rilasciato con licenza [GPL v3](https://www.gnu.org/licenses/gpl-3.0).

## Supporto

- **Issues**: [GitHub Issues](https://github.com/link-it/govpay-tracciati-batch/issues)
- **GovPay**: [govpay.readthedocs.io](https://govpay.readthedocs.io/)

---

Sviluppato da [Link.it s.r.l.](https://www.link.it)

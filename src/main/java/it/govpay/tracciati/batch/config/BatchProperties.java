/*
 * GovPay - Porta di Accesso al Nodo dei Pagamenti SPC
 * http://www.gov4j.it/govpay
 *
 * Copyright (c) 2014-2026 Link.it srl (http://www.link.it).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3, as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package it.govpay.tracciati.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Proprietà di configurazione del batch tracciati (prefix {@code govpay.batch}).
 *
 * <p>Parallelismo configurabile, con la stessa semantica della procedura legacy (dimensione pool e
 * numero di elementi elaborati per thread), separatamente per la fase di caricamento e per la fase
 * di stampa avvisi.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "govpay.batch")
@Data
public class BatchProperties {

	/** Abilita/disabilita l'elaborazione batch. */
	private boolean enabled = true;

	/** Numero di operazioni saltabili (skip) prima di far fallire lo step. */
	private int skipLimit = 10;

	/** Numero massimo di retry per errori transitori (lock DB, timeout HTTP stampe). */
	private int maxRetries = 3;

	// ── Caricamento pendenze ─────────────────────────────────────────────
	/** Dimensione del pool di thread per il caricamento (legacy: it.govpay.thread.pool.caricamentoTracciati). */
	private int caricamentoPoolSize = 10;
	/** Versamenti elaborati per thread/chunk (legacy: numeroVersamentiPerThread). */
	private int caricamentoChunkSize = 100;

	// ── Stampa avvisi ────────────────────────────────────────────────────
	/** Dimensione del pool di thread per le stampe (legacy: ...caricamentoTracciati.stampeAvvisiPagamento). */
	private int stampePoolSize = 10;
	/** Avvisi stampati per thread/lotto (legacy: numeroAvvisiDaStamparePerThread). */
	private int stampeChunkSize = 100;
}

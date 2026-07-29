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
 */
@Configuration
@ConfigurationProperties(prefix = "govpay.batch")
@Data
public class BatchProperties {

	/** Abilita/disabilita l'elaborazione batch. */
	private boolean enabled = true;

	/** Dimensione del pool di thread per gli step partizionati (caricamento e stampe). */
	private int threadPoolSize = 10;

	/** Chunk size dello step di caricamento pendenze (numero versamenti per lotto). */
	private int caricamentoChunkSize = 100;

	/** Chunk size dello step di stampa avvisi (numero avvisi per lotto). */
	private int stampeChunkSize = 100;

	/** Numero di operazioni saltabili (skip) prima di far fallire lo step. */
	private int skipLimit = 10;

	/** Numero massimo di retry per errori transitori (lock DB, timeout HTTP stampe). */
	private int maxRetries = 3;
}

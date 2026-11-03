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

import java.time.ZoneId;

import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.common.batch.service.JobConcurrencyService;

/**
 * Configurazione dei bean infrastrutturali per la gestione batch multi-nodo (govpay-common).
 * <ul>
 *   <li>{@link JobConcurrencyService} — prevenzione esecuzione concorrente e gestione job stale;</li>
 *   <li>{@link JobExecutionHelper} — esecuzione job con parametri standard e controllo pre-esecuzione.</li>
 * </ul>
 * Il lock/lease multi-nodo si appoggia alle tabelle {@code BATCH_*} di Spring Batch, al
 * cluster-id del nodo ({@link #CLUSTER_ID}) e alla proprietà
 * {@code govpay.batch.stale-threshold-minutes}.
 */
@Configuration
public class BatchInfraConfig {

	/**
	 * Cluster-id del nodo: {@code govpay.batch.cluster-id} (o la variabile
	 * {@code GOVPAY_BATCH_CLUSTER_ID}) se impostata, altrimenti l'identificativo
	 * dell'host ({@link it.govpay.common.batch.config.ClusterIdPredefinito}).
	 * Deve essere diverso su ogni nodo (govpay-common#36).
	 */
	public static final String CLUSTER_ID =
			"${govpay.batch.cluster-id:#{T(it.govpay.common.batch.config.ClusterIdPredefinito).valore()}}";

	@Bean
	public JobConcurrencyService jobConcurrencyService(
			JobRepository jobRepository,
			@Value("${govpay.batch.stale-threshold-minutes:120}") int staleThresholdMinutes) {
		return new JobConcurrencyService(jobRepository, staleThresholdMinutes);
	}

	@Bean
	public JobExecutionHelper jobExecutionHelper(
			JobOperator jobOperator,
			JobConcurrencyService jobConcurrencyService,
			@Value(CLUSTER_ID) String clusterId,
			ZoneId applicationZoneId) {
		return new JobExecutionHelper(jobOperator, jobConcurrencyService, clusterId, applicationZoneId);
	}
}

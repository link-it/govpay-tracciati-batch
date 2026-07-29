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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * Task executor per l'elaborazione parallela interna degli step, con concorrenza limitata
 * (bounded) e configurabile da properties: uno per il caricamento pendenze, uno per la stampa avvisi.
 * <p>In una configurazione dedicata (senza dipendenze JPA) per evitare la dipendenza circolare
 * introdotta da Spring Boot 4 tra {@code entityManagerFactoryBuilder} e la configurazione dei job.
 */
@Configuration
public class BatchTaskExecutorConfig {

	private final BatchProperties batchProperties;

	public BatchTaskExecutorConfig(BatchProperties batchProperties) {
		this.batchProperties = batchProperties;
	}

	@Bean
	public SimpleAsyncTaskExecutor caricamentoTaskExecutor() {
		SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("tracciati-caricamento-");
		executor.setConcurrencyLimit(this.batchProperties.getCaricamentoPoolSize());
		return executor;
	}

	@Bean
	public SimpleAsyncTaskExecutor stampeTaskExecutor() {
		SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("tracciati-stampe-");
		executor.setConcurrencyLimit(this.batchProperties.getStampePoolSize());
		return executor;
	}
}

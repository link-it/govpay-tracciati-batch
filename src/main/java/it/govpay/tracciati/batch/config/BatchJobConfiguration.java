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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import it.govpay.tracciati.batch.Costanti;
import it.govpay.tracciati.batch.listener.BatchExecutionRecapListener;

/**
 * Configurazione del job Spring Batch di elaborazione dei tracciati di caricamento pendenze.
 *
 * <p><b>Ossatura (Punto 4):</b> gli step sono al momento segnaposto (tasklet di log).
 * Il flusso ricalca il ciclo di vita del tracciato:</p>
 * <ol>
 *   <li>{@code caricamentoPendenzeStep} — caricamento CSV/JSON (Punto 5, partizionato);</li>
 *   <li>{@code produzioneEsitoStep} — esito JSON/CSV (Punto 6);</li>
 *   <li>{@code stampaAvvisiStep} — stampe via microservizio (Punto 8, condizionale/partizionato);</li>
 *   <li>{@code finalizzazioneStep} — stato finale del tracciato (Punto 9).</li>
 * </ol>
 * <p>La condizionalità dello step di stampa e il partizionamento interno verranno introdotti
 * nei punti successivi.</p>
 */
@Configuration
public class BatchJobConfiguration {

	private static final Logger log = LoggerFactory.getLogger(BatchJobConfiguration.class);

	private final JobRepository jobRepository;
	private final PlatformTransactionManager transactionManager;

	public BatchJobConfiguration(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
		this.jobRepository = jobRepository;
		this.transactionManager = transactionManager;
	}

	@Bean(name = Costanti.ELABORAZIONE_TRACCIATI_PENDENZE_JOB_NAME)
	public Job elaborazioneTracciatiPendenzeJob(
			Step caricamentoPendenzeStep,
			Step produzioneEsitoStep,
			Step stampaAvvisiStep,
			Step finalizzazioneStep,
			BatchExecutionRecapListener batchExecutionRecapListener) {
		return new JobBuilder(Costanti.ELABORAZIONE_TRACCIATI_PENDENZE_JOB_NAME, this.jobRepository)
				.incrementer(new RunIdIncrementer())
				.listener(batchExecutionRecapListener)
				.start(caricamentoPendenzeStep)
				.next(produzioneEsitoStep)
				.next(stampaAvvisiStep)
				.next(finalizzazioneStep)
				.build();
	}

	@Bean
	public Step caricamentoPendenzeStep() {
		return placeholderStep("caricamentoPendenzeStep", "caricamento pendenze CSV/JSON (Punto 5)");
	}

	@Bean
	public Step produzioneEsitoStep() {
		return placeholderStep("produzioneEsitoStep", "produzione esito JSON/CSV (Punto 6)");
	}

	@Bean
	public Step stampaAvvisiStep() {
		return placeholderStep("stampaAvvisiStep", "stampa avvisi via microservizio (Punto 8)");
	}

	@Bean
	public Step finalizzazioneStep() {
		return placeholderStep("finalizzazioneStep", "finalizzazione stato tracciato (Punto 9)");
	}

	/**
	 * Crea uno step tasklet segnaposto che logga e termina con esito COMPLETED.
	 * Sostituito dalle implementazioni reali nei punti successivi.
	 */
	private Step placeholderStep(String stepName, String descrizione) {
		Tasklet tasklet = (contribution, chunkContext) -> {
			log.info("[{}] segnaposto - {} (non ancora implementato)", stepName, descrizione);
			return RepeatStatus.FINISHED;
		};
		return new StepBuilder(stepName, this.jobRepository)
				.tasklet(tasklet, this.transactionManager)
				.build();
	}
}

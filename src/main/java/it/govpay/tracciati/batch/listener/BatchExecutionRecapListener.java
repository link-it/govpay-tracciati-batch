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
package it.govpay.tracciati.batch.listener;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

import it.govpay.common.batch.listener.AbstractBatchExecutionListener;
import it.govpay.tracciati.batch.config.ElaborazioneTracciatoContext;
import it.govpay.tracciati.batch.service.FinalizzazioneTracciatoService;

/**
 * Listener di riepilogo dell'esecuzione del job tracciati (log inizio/fine + statistiche step),
 * basato sul framework di govpay-common.
 *
 * <p>Se l'esecuzione termina in errore, il tracciato elaborato viene scartato (stato
 * {@code SCARTATO}, come nella procedura legacy): altrimenti resterebbe in {@code ELABORAZIONE}
 * o {@code IN_STAMPA} e verrebbe ripreso, e fallirebbe di nuovo, a ogni esecuzione, bloccando i
 * tracciati successivi.</p>
 */
@Component
public class BatchExecutionRecapListener extends AbstractBatchExecutionListener {

	private static final Logger log = LoggerFactory.getLogger(BatchExecutionRecapListener.class);

	private final ElaborazioneTracciatoContext context;
	private final FinalizzazioneTracciatoService finalizzazioneService;

	public BatchExecutionRecapListener(ElaborazioneTracciatoContext context,
			FinalizzazioneTracciatoService finalizzazioneService) {
		this.context = context;
		this.finalizzazioneService = finalizzazioneService;
	}

	@Override
	protected String getBatchName() {
		return "Elaborazione Tracciati Pendenze";
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		if (jobExecution.getStatus() == BatchStatus.FAILED) {
			scartaTracciato(jobExecution);
		}
		super.afterJob(jobExecution);
	}

	private void scartaTracciato(JobExecution jobExecution) {
		try {
			if (!this.context.isPresente()) {
				return;
			}
			String errore = descrizioneErrore(jobExecution.getAllFailureExceptions());
			log.error("Elaborazione del tracciato {} fallita, il tracciato viene scartato: {}",
					this.context.getTracciato().getId(), errore);
			this.finalizzazioneService.scarta(this.context.getTracciato(), this.context.getBeanDati(), errore);
		} catch (Exception e) {
			// il tracciato resta nello stato corrente e sara' ripreso alla prossima esecuzione
			log.error("Impossibile scartare il tracciato dell'esecuzione {}: {}", jobExecution.getId(), e.getMessage(), e);
		}
	}

	private static String descrizioneErrore(List<Throwable> errori) {
		if (errori == null || errori.isEmpty()) {
			return "errore non specificato";
		}
		Throwable causa = errori.get(0);
		while (causa.getCause() != null && causa.getCause() != causa) {
			causa = causa.getCause();
		}
		return causa.getMessage() != null ? causa.getMessage() : causa.getClass().getSimpleName();
	}

	@Override
	protected void printStepStatistics(JobExecution jobExecution) {
		int stepNumber = 1;
		for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
			printSimpleStepStats(stepExecution, stepNumber++, stepExecution.getStepName());
		}
	}
}

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

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import it.govpay.common.batch.runner.AbstractScheduledJobRunner;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.tracciati.batch.Costanti;

/**
 * Runner per l'esecuzione schedulata del job di elaborazione tracciati in modalità multi-nodo.
 * <p>Attivo solo con profile "default" (non "cron").
 */
@Component
@Profile("default")
@EnableScheduling
public class ScheduledJobRunner extends AbstractScheduledJobRunner {

	public ScheduledJobRunner(
			JobExecutionHelper jobExecutionHelper,
			@Qualifier(Costanti.ELABORAZIONE_TRACCIATI_PENDENZE_JOB_NAME) Job elaborazioneTracciatiPendenzeJob) {
		super(jobExecutionHelper, elaborazioneTracciatiPendenzeJob, Costanti.ELABORAZIONE_TRACCIATI_PENDENZE_JOB_NAME);
	}

	@Scheduled(
		fixedDelayString = "${scheduler.elaborazioneTracciatiPendenzeJob.fixedDelayString:60000}",
		initialDelayString = "${scheduler.initialDelayString:1}"
	)
	public JobExecution runBatchElaborazioneTracciatiPendenze() throws JobExecutionAlreadyRunningException,
			JobRestartException, JobInstanceAlreadyCompleteException, InvalidJobParametersException {
		return executeScheduledJob();
	}
}

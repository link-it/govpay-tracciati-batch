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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import it.govpay.common.batch.runner.AbstractCronJobRunner;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.tracciati.batch.Costanti;

/**
 * Runner per l'esecuzione one-shot da command line (cron esterno / CronJob K8s) del job
 * di elaborazione tracciati in modalità multi-nodo.
 * <p>Attivo solo con profile "cron" (non "default").
 */
@Component
@Profile("cron")
public class CronJobRunner extends AbstractCronJobRunner {

	public CronJobRunner(
			JobExecutionHelper jobExecutionHelper,
			@Qualifier(Costanti.ELABORAZIONE_TRACCIATI_PENDENZE_JOB_NAME) Job elaborazioneTracciatiPendenzeJob) {
		super(jobExecutionHelper, elaborazioneTracciatiPendenzeJob, Costanti.ELABORAZIONE_TRACCIATI_PENDENZE_JOB_NAME);
	}
}

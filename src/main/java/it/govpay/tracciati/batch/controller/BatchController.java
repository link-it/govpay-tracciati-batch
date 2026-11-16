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
package it.govpay.tracciati.batch.controller;

import java.time.ZoneId;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.common.batch.controller.AbstractBatchController;
import it.govpay.common.batch.dto.BatchStatusInfo;
import it.govpay.common.batch.dto.LastExecutionInfo;
import it.govpay.common.batch.dto.NextExecutionInfo;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.tracciati.batch.Costanti;
import jakarta.persistence.EntityManager;

/**
 * Controller REST per l'avvio on-demand e il monitoraggio del job di elaborazione tracciati.
 */
@RestController
@RequestMapping("/api/batch")
public class BatchController extends AbstractBatchController {

	private final Job elaborazioneTracciatiPendenzeJob;

	public BatchController(
			JobExecutionHelper jobExecutionHelper,
			JobRepository jobRepository,
			@Qualifier(Costanti.ELABORAZIONE_TRACCIATI_PENDENZE_JOB_NAME) Job elaborazioneTracciatiPendenzeJob,
			Environment environment,
			ZoneId applicationZoneId,
			@Value("${scheduler.elaborazioneTracciatiPendenzeJob.fixedDelayString:60000}") long schedulerIntervalMillis,
			EntityManager entityManager) {
		super(jobExecutionHelper, jobRepository, environment, applicationZoneId, schedulerIntervalMillis, entityManager);
		this.elaborazioneTracciatiPendenzeJob = elaborazioneTracciatiPendenzeJob;
	}

	@Override
	protected Job getJob() {
		return this.elaborazioneTracciatiPendenzeJob;
	}

	@Override
	protected String getJobName() {
		return Costanti.ELABORAZIONE_TRACCIATI_PENDENZE_JOB_NAME;
	}

	@Override
	protected String getDisplayName() {
		return "Elaborazione tracciati pendenze";
	}

	@Override
	protected String getDescription() {
		return "Elabora i tracciati di caricamento e annullamento delle pendenze caricati dagli enti creditori.";
	}

	@GetMapping("/run")
	public ResponseEntity<Object> eseguiJobEndpoint(
			@RequestParam(name = "force", required = false, defaultValue = "false") boolean force) {
		return eseguiJob(force);
	}

	@GetMapping("/status")
	public ResponseEntity<BatchStatusInfo> getStatusEndpoint() {
		return getStatus();
	}

	@GetMapping("/lastExecution")
	public ResponseEntity<LastExecutionInfo> getLastExecutionEndpoint() {
		return getLastExecution();
	}

	@GetMapping("/nextExecution")
	public ResponseEntity<NextExecutionInfo> getNextExecutionEndpoint() {
		return getNextExecution();
	}

	@Override
	protected ResponseEntity<String> clearCache() {
		// Nessuna cache applicativa da svuotare in questa fase.
		return ResponseEntity.ok("Nessuna cache da svuotare");
	}
}

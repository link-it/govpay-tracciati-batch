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
package it.govpay.tracciati.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifica l'assemblaggio del Job: con lo schema di dominio creato su H2 e nessun tracciato pendente,
 * il job attraversa tutti gli step (tutti no-op tramite le guardie di stato) e termina COMPLETATO.
 * Valida il wiring end-to-end della catena di step (contesto @JobScope, reader/writer @StepScope, tasklet).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
class JobAssemblyIntegrationTest {

	@Autowired
	private JobOperator jobOperator;

	@Autowired
	@Qualifier(Costanti.ELABORAZIONE_TRACCIATI_PENDENZE_JOB_NAME)
	private Job elaborazioneTracciatiPendenzeJob;

	@Test
	void jobSenzaTracciatiPendentiTerminaCompletato() throws Exception {
		JobParameters params = new JobParametersBuilder()
				.addLong("run", System.nanoTime())
				.toJobParameters();

		JobExecution execution = this.jobOperator.start(this.elaborazioneTracciatiPendenzeJob, params);

		assertEquals(BatchStatus.COMPLETED, execution.getStatus());
	}
}

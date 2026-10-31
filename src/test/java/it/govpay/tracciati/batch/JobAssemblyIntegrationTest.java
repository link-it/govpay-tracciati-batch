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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;


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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import it.govpay.tracciati.batch.entity.FormatoTracciato;
import it.govpay.tracciati.batch.entity.StatoElaborazione;
import it.govpay.tracciati.batch.entity.TipoTracciato;
import it.govpay.tracciati.batch.entity.Tracciato;
import it.govpay.tracciati.batch.repository.TracciatoRepository;

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

	@Autowired
	private TracciatoRepository tracciatoRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void jobSenzaTracciatiPendentiTerminaCompletato() throws Exception {
		JobParameters params = new JobParametersBuilder()
				.addLong("run", System.nanoTime())
				.toJobParameters();

		JobExecution execution = this.jobOperator.start(this.elaborazioneTracciatiPendenzeJob, params);

		assertEquals(BatchStatus.COMPLETED, execution.getStatus());
	}

	/**
	 * Un tracciato che fa fallire l'esecuzione (qui un CSV, non ancora supportato) viene scartato:
	 * se restasse in ELABORAZIONE verrebbe ripreso, e fallirebbe, a ogni esecuzione successiva.
	 */
	@Test
	void tracciatoCheFaFallireIlJobVieneScartato() throws Exception {
		// Con ddl-auto Hibernate crea bean_dati come VARCHAR(255); nello schema GovPay e' TEXT/CLOB
		// e il bean_dati di un tracciato scartato supera i 255 caratteri.
		this.jdbcTemplate.execute("ALTER TABLE tracciati ALTER COLUMN bean_dati SET DATA TYPE CLOB");
		Tracciato tracciato = this.tracciatoRepository.save(Tracciato.builder()
				.codDominio("12345678901")
				.formato(FormatoTracciato.CSV)
				.tipo(TipoTracciato.PENDENZA)
				.stato(StatoElaborazione.ELABORAZIONE)
				.dataCaricamento(LocalDateTime.now())
				.fileNameRichiesta("tracciato.csv")
				.build());
		try {
			JobParameters params = new JobParametersBuilder()
					.addLong("run", System.nanoTime())
					.toJobParameters();

			JobExecution execution = this.jobOperator.start(this.elaborazioneTracciatiPendenzeJob, params);

			assertEquals(BatchStatus.FAILED, execution.getStatus());
			Tracciato scartato = this.tracciatoRepository.findById(tracciato.getId()).orElseThrow();
			assertEquals(StatoElaborazione.SCARTATO, scartato.getStato());
			assertNotNull(scartato.getDataCompletamento());
			assertTrue(scartato.getDescrizioneStato().startsWith("Errore durante l'elaborazione del tracciato"));
		} finally {
			this.tracciatoRepository.deleteById(tracciato.getId());
		}
	}
}

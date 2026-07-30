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
package it.govpay.tracciati.batch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import it.govpay.tracciati.batch.dto.TracciatoPendenza;
import it.govpay.tracciati.batch.entity.StatoElaborazione;
import it.govpay.tracciati.batch.entity.Tracciato;
import it.govpay.tracciati.batch.gde.GdeService;
import it.govpay.tracciati.batch.metrics.TracciatiMetrics;
import it.govpay.tracciati.batch.repository.TracciatoRepository;
import tools.jackson.databind.json.JsonMapper;

class FinalizzazioneTracciatoServiceTest {

	private final TracciatoRepository tracciatoRepository = mock(TracciatoRepository.class);
	private final GdeService gdeService = mock(GdeService.class);
	private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
	private final FinalizzazioneTracciatoService service = new FinalizzazioneTracciatoService(
			this.tracciatoRepository, JsonMapper.builder().build(), new TracciatiMetrics(this.meterRegistry), this.gdeService);

	@Test
	void completaCaricamentoSenzaStampeVaInCompletato() {
		Tracciato tracciato = Tracciato.builder().id(1L).build();
		TracciatoPendenza beanDati = new TracciatoPendenza();
		beanDati.setNumAddOk(5);
		beanDati.setStampaAvvisi(false);

		this.service.completaCaricamento(tracciato, beanDati);

		assertEquals(StatoElaborazione.COMPLETATO, tracciato.getStato());
		assertNotNull(tracciato.getDataCompletamento());
		assertTrue(tracciato.getBeanDati().contains("CARICAMENTO_OK"));
		assertEquals(1.0, this.meterRegistry.get("govpay.tracciati.completati").counter().count());
		verify(this.tracciatoRepository, times(1)).save(any(Tracciato.class));
		verify(this.gdeService).inviaEsitoElaborazione(any(Tracciato.class), org.mockito.ArgumentMatchers.eq(true));
	}

	@Test
	void completaCaricamentoConStampeVaInStampaEConErroriMarcaKo() {
		Tracciato tracciato = Tracciato.builder().id(1L).build();
		TracciatoPendenza beanDati = new TracciatoPendenza();
		beanDati.setNumAddOk(3);
		beanDati.setNumAddKo(2);
		beanDati.setStampaAvvisi(true);

		this.service.completaCaricamento(tracciato, beanDati);

		assertEquals(StatoElaborazione.IN_STAMPA, tracciato.getStato());
		assertEquals(3, beanDati.getNumStampeTotali());
		assertTrue(tracciato.getBeanDati().contains("CARICAMENTO_KO"));
	}

	@Test
	void scartaImpostaStatoScartatoEAnnullato() {
		Tracciato tracciato = Tracciato.builder().id(1L).build();
		TracciatoPendenza beanDati = new TracciatoPendenza();

		this.service.scarta(tracciato, beanDati, "boom");

		assertEquals(StatoElaborazione.SCARTATO, tracciato.getStato());
		assertNotNull(tracciato.getDescrizioneStato());
		assertTrue(tracciato.getDescrizioneStato().contains("boom"));
		assertTrue(tracciato.getBeanDati().contains("ANNULLATO"));
		assertEquals(1.0, this.meterRegistry.get("govpay.tracciati.scartati").counter().count());
		verify(this.gdeService).inviaEsitoElaborazione(any(Tracciato.class), org.mockito.ArgumentMatchers.eq(false));
	}
}

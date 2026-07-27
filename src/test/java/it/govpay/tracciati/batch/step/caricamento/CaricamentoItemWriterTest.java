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
package it.govpay.tracciati.batch.step.caricamento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.Chunk;

import it.govpay.tracciati.batch.dto.EsitoCaricamento;
import it.govpay.tracciati.batch.dto.PendenzaPost;
import it.govpay.tracciati.batch.dto.RigaTracciato;
import it.govpay.tracciati.batch.dto.TracciatoPendenza;
import it.govpay.tracciati.batch.entity.Operazione;
import it.govpay.tracciati.batch.entity.StatoOperazione;
import it.govpay.tracciati.batch.entity.TipoOperazione;
import it.govpay.tracciati.batch.entity.Tracciato;
import it.govpay.tracciati.batch.repository.OperazioneRepository;
import it.govpay.tracciati.batch.service.CaricamentoService;

class CaricamentoItemWriterTest {

	private final CaricamentoService caricamentoService = mock(CaricamentoService.class);
	private final OperazioneRepository operazioneRepository = mock(OperazioneRepository.class);

	private CaricamentoItemWriter nuovoWriter(Tracciato tracciato, TracciatoPendenza beanDati) {
		when(this.operazioneRepository.findByIdTracciatoAndLineaElaborazione(any(), any())).thenReturn(Optional.empty());
		when(this.operazioneRepository.save(any(Operazione.class))).thenAnswer(inv -> inv.getArgument(0));
		CaricamentoItemWriter writer = new CaricamentoItemWriter(this.caricamentoService, this.operazioneRepository);
		writer.bind(tracciato, beanDati);
		return writer;
	}

	@Test
	void addValidaRegistraOperazioneOkEIncrementaContatori() throws Exception {
		Tracciato tracciato = Tracciato.builder().id(1L).codDominio("DOM01").build();
		TracciatoPendenza beanDati = new TracciatoPendenza();

		PendenzaPost pendenza = new PendenzaPost();
		pendenza.setIdPendenza("P1");
		RigaTracciato riga = new RigaTracciato();
		riga.setLinea(1L);
		riga.setTipoOperazione(TipoOperazione.ADD);
		riga.setPendenza(pendenza);
		riga.setJsonRichiesta("{}");

		when(this.caricamentoService.caricaPendenza(eq(pendenza), eq(tracciato)))
				.thenReturn(EsitoCaricamento.builder()
						.stato(StatoOperazione.ESEGUITO_OK)
						.idVersamento(5L)
						.codVersamentoEnte("P1")
						.build());

		CaricamentoItemWriter writer = nuovoWriter(tracciato, beanDati);
		writer.write(new Chunk<>(List.of(riga)));

		verify(this.operazioneRepository, times(1)).save(any(Operazione.class));
		assertEquals(1, beanDati.getNumAddOk());
		assertEquals(0, beanDati.getNumAddKo());
		assertEquals(1, beanDati.getLineaElaborazioneAdd());
	}

	@Test
	void rigaNonValidaRegistraKoSenzaChiamareIlServizio() throws Exception {
		Tracciato tracciato = Tracciato.builder().id(1L).codDominio("DOM01").build();
		TracciatoPendenza beanDati = new TracciatoPendenza();

		RigaTracciato riga = new RigaTracciato();
		riga.setLinea(1L);
		riga.setTipoOperazione(TipoOperazione.ADD);
		riga.setPendenza(new PendenzaPost());
		riga.setErrore("idPendenza obbligatorio");

		CaricamentoItemWriter writer = nuovoWriter(tracciato, beanDati);
		writer.write(new Chunk<>(List.of(riga)));

		verify(this.caricamentoService, times(0)).caricaPendenza(any(), any());
		verify(this.operazioneRepository, times(1)).save(any(Operazione.class));
		assertEquals(0, beanDati.getNumAddOk());
		assertEquals(1, beanDati.getNumAddKo());
	}
}

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
package it.govpay.tracciati.batch.stampe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import it.govpay.tracciati.batch.dto.RisultatoStampa;
import it.govpay.tracciati.batch.entity.Documento;
import it.govpay.tracciati.batch.entity.Stampa;
import it.govpay.tracciati.batch.entity.Versamento;
import it.govpay.tracciati.batch.repository.StampaRepository;
import it.govpay.tracciati.stampe.client.model.CdsViolation;
import it.govpay.tracciati.stampe.client.model.Creditor;
import it.govpay.tracciati.stampe.client.model.PaymentNotice;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Orchestrazione della stampa: scelta dell'endpoint, salvataggio e gestione degli errori. */
class StampaAvvisoServiceTest {

	private final StampeClient stampeClient = mock(StampeClient.class);
	private final StampaRepository stampaRepository = mock(StampaRepository.class);
	private final DatiCreditoreResolver datiCreditoreResolver = mock(DatiCreditoreResolver.class);
	private final IbanAvvisoResolver ibanAvvisoResolver = mock(IbanAvvisoResolver.class);

	private final AvvisoMapper avvisoMapper = new AvvisoMapper(
			new ProprietaPendenzaReader(JsonMapper.builder()
					.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()),
			null);

	private final StampaAvvisoService service = new StampaAvvisoService(this.avvisoMapper, this.stampeClient,
			this.stampaRepository, this.datiCreditoreResolver, this.ibanAvvisoResolver);

	private Versamento versamento(long id, String codRata) {
		return Versamento.builder().id(id).idDominio(1L).codRata(codRata).codVersamentoEnte("P" + id)
				.debitoreIdentificativo("RSSMRA80A01H501U").debitoreAnagrafica("Mario Rossi")
				.importoTotale(50.0).numeroAvviso("30100000000000000" + id).build();
	}

	private void anagraficaDisponibile() {
		when(this.datiCreditoreResolver.risolvi(any(Versamento.class))).thenReturn(new DatiCreditore(
				new Creditor().fiscalCode("01234567890").businessName("Comune"), new byte[] {1}, null));
		when(this.ibanAvvisoResolver.ibanPostale(any(Versamento.class))).thenReturn(Optional.empty());
	}

	@Test
	void stampaDiUnaPosizioneSalvaLaStampaSulVersamento() {
		anagraficaDisponibile();
		when(this.stampaRepository.findByIdVersamento(1L)).thenReturn(Optional.empty());
		when(this.stampeClient.creaAvvisoStandard(any(PaymentNotice.class))).thenReturn(new byte[] {9, 8, 7});

		RisultatoStampa esito = this.service.stampa(AvvisoDaStampare.diVersamento(versamento(1, null)));

		assertTrue(esito.ok());
		assertArrayEquals(new byte[] {9, 8, 7}, esito.pdf());
		assertEquals("01234567890", esito.codDominio());
		ArgumentCaptor<Stampa> captor = ArgumentCaptor.forClass(Stampa.class);
		verify(this.stampaRepository).save(captor.capture());
		assertEquals(1L, captor.getValue().getIdVersamento());
		assertEquals(null, captor.getValue().getIdDocumento());
	}

	@Test
	void stampaDiUnDocumentoSalvaUnaSolaStampaSulDocumento() {
		anagraficaDisponibile();
		when(this.stampaRepository.findByIdDocumento(7L)).thenReturn(Optional.empty());
		when(this.stampeClient.creaAvvisoStandard(any(PaymentNotice.class))).thenReturn(new byte[] {1});
		Documento documento = Documento.builder().id(7L).codDocumento("DOC1").descrizione("Rette").build();

		RisultatoStampa esito = this.service.stampa(AvvisoDaStampare.diDocumento(documento,
				List.of(versamento(1, "1"), versamento(2, "2"))));

		assertTrue(esito.ok());
		assertEquals("DOC1", esito.numeroDocumento());
		ArgumentCaptor<Stampa> captor = ArgumentCaptor.forClass(Stampa.class);
		verify(this.stampaRepository, times(1)).save(captor.capture());
		assertEquals(7L, captor.getValue().getIdDocumento());
	}

	@Test
	void violazioneCdsUsaEndpointDedicato() {
		anagraficaDisponibile();
		when(this.stampaRepository.findByIdDocumento(8L)).thenReturn(Optional.empty());
		when(this.stampeClient.creaAvvisoViolazioneCds(any(CdsViolation.class))).thenReturn(new byte[] {2});
		Documento documento = Documento.builder().id(8L).codDocumento("CDS1").descrizione("Violazione").build();

		RisultatoStampa esito = this.service.stampa(AvvisoDaStampare.diDocumento(documento,
				List.of(versamento(1, "RIDOTTO"), versamento(2, "SCONTATO"))));

		assertTrue(esito.ok());
		verify(this.stampeClient, never()).creaAvvisoStandard(any());
		verify(this.stampeClient).creaAvvisoViolazioneCds(any(CdsViolation.class));
	}

	@Test
	void senzaNumeroAvvisoNonStampa() {
		Versamento senzaAvviso = Versamento.builder().id(11L).idDominio(1L).codVersamentoEnte("P11").build();

		RisultatoStampa esito = this.service.stampa(AvvisoDaStampare.diVersamento(senzaAvviso));

		assertFalse(esito.ok());
		verify(this.stampeClient, never()).creaAvvisoStandard(any());
	}

	@Test
	void erroreDelServizioProduceEsitoKo() {
		anagraficaDisponibile();
		when(this.stampeClient.creaAvvisoStandard(any(PaymentNotice.class)))
				.thenThrow(new RuntimeException("servizio non disponibile"));

		RisultatoStampa esito = this.service.stampa(AvvisoDaStampare.diVersamento(versamento(1, null)));

		assertFalse(esito.ok());
		verify(this.stampaRepository, never()).save(any());
	}
}

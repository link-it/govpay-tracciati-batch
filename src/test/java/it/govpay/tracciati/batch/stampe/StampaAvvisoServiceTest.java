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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import it.govpay.tracciati.batch.dto.RisultatoStampa;
import it.govpay.tracciati.batch.entity.Stampa;
import it.govpay.tracciati.batch.entity.Versamento;
import it.govpay.tracciati.batch.repository.DocumentoRepository;
import it.govpay.tracciati.batch.repository.StampaRepository;
import it.govpay.tracciati.stampe.client.model.Creditor;
import it.govpay.tracciati.stampe.client.model.PaymentNotice;

class StampaAvvisoServiceTest {

	private final StampeClient stampeClient = mock(StampeClient.class);
	private final StampaRepository stampaRepository = mock(StampaRepository.class);
	private final DocumentoRepository documentoRepository = mock(DocumentoRepository.class);
	private final DatiAvvisoResolver datiAvvisoResolver = mock(DatiAvvisoResolver.class);

	private final StampaAvvisoService service = new StampaAvvisoService(
			new PaymentNoticeMapper(), this.stampeClient, this.stampaRepository, this.documentoRepository, this.datiAvvisoResolver);

	private Versamento versamentoConAvviso() {
		return Versamento.builder().id(10L).idDominio(1L).codVersamentoEnte("P1")
				.debitoreIdentificativo("RSSMRA80A01H501U").debitoreAnagrafica("Mario Rossi")
				.importoTotale(50.0).numeroAvviso("301000000000000123").build();
	}

	@Test
	void stampaOkSalvaStampaERitornaPdf() {
		Versamento v = versamentoConAvviso();
		when(this.datiAvvisoResolver.risolvi(v)).thenReturn(
				new DatiAvvisoCreditore(new Creditor().fiscalCode("01234567890").businessName("Comune"), null, "qr", null, null, null));
		when(this.stampeClient.creaAvvisoStandard(any(PaymentNotice.class))).thenReturn(new byte[]{9, 8, 7});

		RisultatoStampa esito = this.service.stampa(v);

		assertTrue(esito.ok());
		assertArrayEquals(new byte[]{9, 8, 7}, esito.pdf());
		verify(this.stampaRepository, times(1)).save(any(Stampa.class));
	}

	@Test
	void senzaNumeroAvvisoNonStampa() {
		Versamento v = Versamento.builder().id(11L).idDominio(1L).build();
		RisultatoStampa esito = this.service.stampa(v);
		assertFalse(esito.ok());
		verify(this.stampeClient, times(0)).creaAvvisoStandard(any());
	}

	@Test
	void erroreClientProduceEsitoKo() {
		Versamento v = versamentoConAvviso();
		when(this.datiAvvisoResolver.risolvi(v)).thenReturn(
				new DatiAvvisoCreditore(new Creditor().fiscalCode("01234567890").businessName("Comune"), null, "qr", null, null, null));
		when(this.stampeClient.creaAvvisoStandard(any(PaymentNotice.class))).thenThrow(new RuntimeException("servizio non disponibile"));

		RisultatoStampa esito = this.service.stampa(v);
		assertFalse(esito.ok());
	}
}

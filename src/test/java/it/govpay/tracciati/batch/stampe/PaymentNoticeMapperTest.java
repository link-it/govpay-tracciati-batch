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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import it.govpay.tracciati.batch.entity.Versamento;
import it.govpay.tracciati.stampe.client.model.Creditor;
import it.govpay.tracciati.stampe.client.model.Languages;
import it.govpay.tracciati.stampe.client.model.PaymentNotice;

class PaymentNoticeMapperTest {

	private final PaymentNoticeMapper mapper = new PaymentNoticeMapper();

	@Test
	void mappaDebitoreImportoEMetadatiDefault() {
		Versamento v = Versamento.builder()
				.debitoreIdentificativo("RSSMRA80A01H501U")
				.debitoreAnagrafica("Mario Rossi")
				.debitoreIndirizzo("Via Roma").debitoreCivico("10")
				.debitoreCap("00100").debitoreLocalita("Roma").debitoreProvincia("RM")
				.importoTotale(123.45)
				.dataScadenza(LocalDateTime.of(2026, 9, 30, 0, 0))
				.numeroAvviso("301000000000000123")
				.build();
		Creditor creditor = new Creditor().fiscalCode("01234567890").businessName("Comune di Test");
		DatiAvvisoCreditore dati = new DatiAvvisoCreditore(creditor, null, "PAGOPA|002|...", null, null, null);

		PaymentNotice pn = this.mapper.toPaymentNotice(v, dati);

		assertEquals("RSSMRA80A01H501U", pn.getDebtor().getFiscalCode());
		assertEquals("Mario Rossi", pn.getDebtor().getFullName());
		assertTrue(pn.getDebtor().getAddressLine1().contains("Via Roma"));
		assertTrue(pn.getDebtor().getAddressLine2().contains("Roma"));
		assertTrue(pn.getDebtor().getAddressLine2().contains("(RM)"));

		assertEquals(123.45, pn.getFull().getAmount(), 0.0001);
		assertEquals(LocalDate.of(2026, 9, 30), pn.getFull().getDueDate());
		assertEquals("301000000000000123", pn.getFull().getNoticeNumber());
		assertEquals("PAGOPA|002|...", pn.getFull().getQrcode());

		assertEquals(Languages.IT, pn.getLanguage());
		assertEquals("Avviso di pagamento", pn.getTitle());
		assertEquals("Comune di Test", pn.getCreditor().getBusinessName());
		assertEquals(Boolean.FALSE, pn.getPostal());
	}
}

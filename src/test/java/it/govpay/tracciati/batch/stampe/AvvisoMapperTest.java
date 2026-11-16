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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import it.govpay.tracciati.batch.entity.Documento;
import it.govpay.tracciati.batch.entity.Versamento;
import it.govpay.tracciati.batch.util.CausaleUtils;
import it.govpay.tracciati.stampe.client.model.CdsViolation;
import it.govpay.tracciati.stampe.client.model.Creditor;
import it.govpay.tracciati.stampe.client.model.Iban;
import it.govpay.tracciati.stampe.client.model.Languages;
import it.govpay.tracciati.stampe.client.model.PaymentNotice;
import it.govpay.tracciati.stampe.client.model.ThresholdType;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Composizione della richiesta di stampa: importi, tipologie, seconda lingua, data di scadenza. */
class AvvisoMapperTest {

	private final ProprietaPendenzaReader proprietaReader = new ProprietaPendenzaReader(
			JsonMapper.builder().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build());
	private final AvvisoMapper mapper = new AvvisoMapper(this.proprietaReader, null);

	private final DatiCreditore creditore = new DatiCreditore(
			new Creditor().fiscalCode("01234567890").businessName("Comune di Test"), new byte[] {1, 2}, null);

	private Versamento versamento(long id, String codRata, double importo) {
		return Versamento.builder()
				.id(id).codRata(codRata).importoTotale(importo)
				.numeroAvviso("30100000000000000" + id)
				.causaleVersamento(CausaleUtils.encode("Tassa rifiuti 2026"))
				.debitoreIdentificativo("RSSMRA80A01H501U").debitoreAnagrafica("Mario Rossi")
				.debitoreIndirizzo("Via Roma").debitoreCivico("10")
				.debitoreCap("00100").debitoreLocalita("Roma").debitoreProvincia("RM")
				.dataScadenza(LocalDateTime.of(2026, 9, 30, 0, 0))
				.build();
	}

	private ConfigurazioneAvviso configurazione(List<Versamento> versamenti, Set<Long> conIbanPostale) {
		return ConfigurazioneAvviso.da(versamenti, conIbanPostale);
	}

	@Test
	void rataUnicaConDebitoreECausaleDecodificata() {
		List<Versamento> versamenti = List.of(versamento(1, null, 123.45));
		AvvisoDaStampare avviso = AvvisoDaStampare.diVersamento(versamenti.get(0));

		PaymentNotice notice = this.mapper.toPaymentNotice(avviso, this.creditore,
				configurazione(versamenti, Set.of()), Map.of());

		assertEquals(Languages.IT, notice.getLanguage());
		// il titolo e' l'oggetto del pagamento, cioe' la causale della pendenza
		assertEquals("Tassa rifiuti 2026", notice.getTitle());
		assertEquals(Boolean.FALSE, notice.getPostal());
		assertEquals("Mario Rossi", notice.getDebtor().getFullName());
		assertTrue(notice.getDebtor().getAddressLine2().contains("(RM)"));
		assertEquals(123.45, notice.getFull().getAmount(), 0.0001);
		assertEquals(LocalDate.of(2026, 9, 30), notice.getFull().getDueDate());
		assertEquals("PAGOPA|002|301000000000000001|01234567890|12345", notice.getFull().getQrcode());
		assertNull(notice.getInstalments());
		assertNull(notice.getFull().getIban());
	}

	@Test
	void documentoConRateProduceInstalments() {
		List<Versamento> versamenti = List.of(versamento(1, "1", 50.0), versamento(2, "2", 50.0));
		Documento documento = Documento.builder().id(7L).codDocumento("DOC1").descrizione("Rette 2026").build();
		AvvisoDaStampare avviso = AvvisoDaStampare.diDocumento(documento, versamenti);

		PaymentNotice notice = this.mapper.toPaymentNotice(avviso, this.creditore,
				configurazione(versamenti, Set.of()), Map.of());

		// per un documento l'oggetto del pagamento e' la sua descrizione
		assertEquals("Rette 2026", notice.getTitle());
		assertNull(notice.getFull());
		assertEquals(2, notice.getInstalments().size());
		assertEquals(1, notice.getInstalments().get(0).getInstalmentNumber());
		assertEquals(2, notice.getInstalments().get(1).getInstalmentNumber());
	}

	@Test
	void rataUnicaConRateRestaNelCampoFull() {
		List<Versamento> versamenti = List.of(versamento(1, null, 100.0), versamento(2, "1", 50.0), versamento(3, "2", 50.0));
		AvvisoDaStampare avviso = AvvisoDaStampare.diDocumento(
				Documento.builder().id(7L).codDocumento("DOC2").descrizione("Rette").build(), versamenti);

		PaymentNotice notice = this.mapper.toPaymentNotice(avviso, this.creditore,
				configurazione(versamenti, Set.of()), Map.of());

		assertEquals(100.0, notice.getFull().getAmount(), 0.0001);
		assertEquals(2, notice.getInstalments().size());
	}

	@Test
	void soglieTemporaliProduconoPagamentiRidotti() {
		List<Versamento> versamenti = List.of(versamento(1, "ENTRO5", 40.0), versamento(2, "OLTRE5", 80.0));
		AvvisoDaStampare avviso = AvvisoDaStampare.diDocumento(
				Documento.builder().id(8L).codDocumento("DOC3").descrizione("Sanzione").build(), versamenti);

		PaymentNotice notice = this.mapper.toPaymentNotice(avviso, this.creditore,
				configurazione(versamenti, Set.of()), Map.of());

		assertNull(notice.getInstalments());
		assertEquals(2, notice.getReducedPayments().size());
		assertEquals(ThresholdType.ENTRO, notice.getReducedPayments().get(0).getThresholdType());
		assertEquals(5, notice.getReducedPayments().get(0).getThresholdDays());
		assertEquals(ThresholdType.OLTRE, notice.getReducedPayments().get(1).getThresholdType());
	}

	@Test
	void avvisoPostaleRiportaIbanSuOgniImporto() {
		List<Versamento> versamenti = List.of(versamento(1, "1", 30.0), versamento(2, "2", 30.0));
		Iban iban = new Iban().ibanCode("IT60X0542811101000000123456").postalAuthMessage("Aut. 1");
		Map<Long, Iban> ibanPostali = Map.of(1L, iban, 2L, iban);
		AvvisoDaStampare avviso = AvvisoDaStampare.diDocumento(
				Documento.builder().id(9L).codDocumento("DOC4").descrizione("Rette").build(), versamenti);

		PaymentNotice notice = this.mapper.toPaymentNotice(avviso, this.creditore,
				configurazione(versamenti, ibanPostali.keySet()), ibanPostali);

		assertEquals(Boolean.TRUE, notice.getPostal());
		assertNotNull(notice.getInstalments().get(0).getIban());
		assertEquals("IT60X0542811101000000123456", notice.getInstalments().get(1).getIban().getIbanCode());
	}

	@Test
	void violazioneCdsMappaImportiScontatoERidotto() {
		Versamento ridotto = versamento(1, "RIDOTTO", 80.0);
		Versamento scontato = versamento(2, "SCONTATO", 56.0);
		List<Versamento> versamenti = List.of(ridotto, scontato);
		AvvisoDaStampare avviso = AvvisoDaStampare.diDocumento(
				Documento.builder().id(10L).codDocumento("CDS1").descrizione("Violazione CDS").build(), versamenti);
		ConfigurazioneAvviso conf = configurazione(versamenti, Set.of());
		assertTrue(conf.violazioneCds());

		CdsViolation violazione = this.mapper.toCdsViolation(avviso, this.creditore, conf, Map.of());

		assertEquals(56.0, violazione.getDiscountedAmount().getAmount(), 0.0001);
		assertEquals(80.0, violazione.getReducedAmount().getAmount(), 0.0001);
		assertEquals("Violazione CDS", violazione.getTitle());
	}

	@Test
	void secondaLinguaDalleProprietaDellaPendenza() {
		Versamento v = versamento(1, null, 10.0);
		v.setProprieta("{\"linguaSecondaria\":\"de\",\"linguaSecondariaCausale\":\"Abfallgebuehr 2026\"}");
		List<Versamento> versamenti = List.of(v);

		PaymentNotice notice = this.mapper.toPaymentNotice(AvvisoDaStampare.diVersamento(v), this.creditore,
				configurazione(versamenti, Set.of()), Map.of());

		assertEquals(Boolean.TRUE, notice.getSecondLanguage().getBilinguism());
		assertEquals(Languages.DE, notice.getSecondLanguage().getLanguage());
		assertEquals("Abfallgebuehr 2026", notice.getSecondLanguage().getTitle());
	}

	@Test
	void secondaLinguaFalseNonProduceBilinguismo() {
		Versamento v = versamento(1, null, 10.0);
		v.setProprieta("{\"linguaSecondaria\":\"false\"}");

		PaymentNotice notice = this.mapper.toPaymentNotice(AvvisoDaStampare.diVersamento(v), this.creditore,
				configurazione(List.of(v), Set.of()), Map.of());

		assertNull(notice.getSecondLanguage());
	}

	@Test
	void dataScadenzaDalleProprietaHaLaPrecedenza() {
		Versamento v = versamento(1, null, 10.0);
		v.setDataValidita(LocalDateTime.of(2026, 10, 15, 0, 0));
		v.setProprieta("{\"dataScandenzaAvviso\":\"2026-12-31\"}");

		assertEquals(LocalDate.of(2026, 12, 31), this.mapper.dataScadenza(v));
	}

	@Test
	void dataValiditaPrecedeDataScadenza() {
		Versamento v = versamento(1, null, 10.0);
		v.setDataValidita(LocalDateTime.of(2026, 10, 15, 0, 0));

		assertEquals(LocalDate.of(2026, 10, 15), this.mapper.dataScadenza(v));
	}

	@Test
	void senzaDateSiUsanoIGiorniDiValiditaConfigurati() {
		AvvisoMapper conGiorni = new AvvisoMapper(this.proprietaReader, 30);
		Versamento v = versamento(1, null, 10.0);
		v.setDataScadenza(null);
		v.setDataCreazione(LocalDateTime.of(2026, 1, 1, 12, 0));

		assertEquals(LocalDate.of(2026, 1, 31), conGiorni.dataScadenza(v));
		assertNull(this.mapper.dataScadenza(v));
	}
}

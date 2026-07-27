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
package it.govpay.tracciati.batch.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import it.govpay.tracciati.batch.dto.PendenzaPost;
import it.govpay.tracciati.batch.dto.Soggetto;
import it.govpay.tracciati.batch.dto.VocePendenza;
import it.govpay.tracciati.batch.entity.SingoloVersamento;
import it.govpay.tracciati.batch.entity.Versamento;

class PendenzaConverterTest {

	private final PendenzaConverter converter = new PendenzaConverter();

	@Test
	void mappaCampiPrincipaliEDefault() {
		PendenzaPost p = new PendenzaPost();
		p.setIdPendenza("PEND-1");
		p.setCausale("Causale test");
		p.setImporto(new BigDecimal("123.45"));
		Soggetto s = new Soggetto();
		s.setIdentificativo("RSSMRA80A01H501U");
		s.setAnagrafica("Mario Rossi");
		p.setSoggettoPagatore(s);

		Versamento v = this.converter.toVersamento(p);

		assertEquals("PEND-1", v.getCodVersamentoEnte());
		assertEquals("Causale test", v.getCausaleVersamento());
		assertEquals(123.45, v.getImportoTotale(), 0.0001);
		assertEquals("RSSMRA80A01H501U", v.getDebitoreIdentificativo());
		assertEquals("Mario Rossi", v.getDebitoreAnagrafica());
		assertEquals(v.getDebitoreIdentificativo(), v.getSrcDebitoreIdentificativo());
		assertFalse(v.isAck());
		assertFalse(v.isAnomalo());
		assertEquals(0d, v.getImportoPagato(), 0.0001);
	}

	@Test
	void debitoreAnonimoSeAssente() {
		PendenzaPost p = new PendenzaPost();
		p.setIdPendenza("PEND-2");
		p.setImporto(new BigDecimal("10.00"));

		Versamento v = this.converter.toVersamento(p);
		assertEquals("ANONIMO", v.getDebitoreIdentificativo());
		assertEquals("ANONIMO", v.getDebitoreAnagrafica());
	}

	@Test
	void mappaVociInSingoliVersamentiConIndiceProgressivo() {
		PendenzaPost p = new PendenzaPost();
		VocePendenza v1 = new VocePendenza();
		v1.setIdVocePendenza("V1");
		v1.setImporto(new BigDecimal("30.00"));
		VocePendenza v2 = new VocePendenza();
		v2.setIdVocePendenza("V2");
		v2.setImporto(new BigDecimal("70.00"));
		p.setVoci(List.of(v1, v2));

		List<SingoloVersamento> singoli = this.converter.toSingoliVersamenti(p);
		assertEquals(2, singoli.size());
		assertEquals("V1", singoli.get(0).getCodSingoloVersamentoEnte());
		assertEquals(1, singoli.get(0).getIndiceDati());
		assertEquals(2, singoli.get(1).getIndiceDati());
		assertEquals(70.00, singoli.get(1).getImportoSingoloVersamento(), 0.0001);
	}
}

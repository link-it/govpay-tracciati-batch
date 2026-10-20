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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import it.govpay.tracciati.batch.entity.Versamento;

/** Classificazione delle posizioni di un avviso (port di {@code AvvisoPagamentoInputConf}). */
class ConfigurazioneAvvisoTest {

	private Versamento versamento(long id, String codRata) {
		return Versamento.builder().id(id).codRata(codRata).numeroAvviso("3010000000000001" + id).build();
	}

	@Test
	void rataUnicaSenzaCodRata() {
		ConfigurazioneAvviso conf = ConfigurazioneAvviso.da(List.of(versamento(1, null)), Set.of());
		assertEquals(1, conf.rateUniche().size());
		assertTrue(conf.rate().isEmpty());
		assertFalse(conf.postale());
		assertFalse(conf.violazioneCds());
	}

	@Test
	void rateOrdinatePerNumero() {
		ConfigurazioneAvviso conf = ConfigurazioneAvviso.da(
				List.of(versamento(1, "3"), versamento(2, "1"), versamento(3, "2")), Set.of());
		assertEquals(List.of(2L, 3L, 1L), conf.rate().stream().map(Versamento::getId).toList());
	}

	@Test
	void soglieTemporaliOrdinatePerGiorni() {
		ConfigurazioneAvviso conf = ConfigurazioneAvviso.da(
				List.of(versamento(1, "OLTRE60"), versamento(2, "ENTRO5")), Set.of());
		assertEquals(List.of(2L, 1L), conf.soglieTemporali().stream().map(Versamento::getId).toList());
	}

	@Test
	void violazioneCdsSoloSeTutteLePosizioniSonoRidottoEScontato() {
		ConfigurazioneAvviso cds = ConfigurazioneAvviso.da(
				List.of(versamento(1, "RIDOTTO"), versamento(2, "SCONTATO")), Set.of());
		assertTrue(cds.violazioneCds());
		assertEquals(1L, cds.ridotto().getId());
		assertEquals(2L, cds.scontato().getId());

		ConfigurazioneAvviso mista = ConfigurazioneAvviso.da(
				List.of(versamento(1, "RIDOTTO"), versamento(2, "SCONTATO"), versamento(3, null)), Set.of());
		assertFalse(mista.violazioneCds());
	}

	@Test
	void postaleSoloSeTutteLePosizioniHannoIbanPostale() {
		List<Versamento> versamenti = List.of(versamento(1, "1"), versamento(2, "2"));
		assertTrue(ConfigurazioneAvviso.da(versamenti, Set.of(1L, 2L)).postale());
		assertFalse(ConfigurazioneAvviso.da(versamenti, Set.of(1L)).postale());
	}
}

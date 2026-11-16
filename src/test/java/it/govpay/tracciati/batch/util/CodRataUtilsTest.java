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
package it.govpay.tracciati.batch.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import it.govpay.tracciati.batch.entity.TipoSogliaVersamento;
import it.govpay.tracciati.batch.util.CodRataUtils.RataSoglia;

/** Decodifica di {@code cod_rata}: rata unica, rate numerate, soglie temporali e soglie CDS. */
class CodRataUtilsTest {

	@Test
	void codRataAssenteEQuindiRataUnica() {
		RataSoglia rataSoglia = CodRataUtils.decodifica(null);
		assertTrue(rataSoglia.isRataUnica());
		assertNull(rataSoglia.numeroRata());
		assertNull(rataSoglia.tipoSoglia());
	}

	@Test
	void codRataNumericoEIlNumeroDiRata() {
		RataSoglia rataSoglia = CodRataUtils.decodifica("3");
		assertEquals(3, rataSoglia.numeroRata());
		assertFalse(rataSoglia.isRataUnica());
		assertNull(rataSoglia.tipoSoglia());
	}

	@Test
	void sogliaTemporaleConGiorni() {
		RataSoglia entro = CodRataUtils.decodifica("ENTRO60");
		assertEquals(TipoSogliaVersamento.ENTRO, entro.tipoSoglia());
		assertEquals(60, entro.giorniSoglia());
		assertTrue(entro.isSogliaTemporale());

		RataSoglia oltre = CodRataUtils.decodifica("OLTRE5");
		assertEquals(TipoSogliaVersamento.OLTRE, oltre.tipoSoglia());
		assertEquals(5, oltre.giorniSoglia());
	}

	@Test
	void soglieViolazioneCds() {
		assertEquals(TipoSogliaVersamento.RIDOTTO, CodRataUtils.decodifica("RIDOTTO").tipoSoglia());
		assertTrue(CodRataUtils.decodifica("SCONTATO").isViolazioneCds());
		assertFalse(CodRataUtils.decodifica("ENTRO10").isViolazioneCds());
	}
}

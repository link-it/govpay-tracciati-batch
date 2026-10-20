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
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Codifica/decodifica della causale nel formato usato dalla colonna {@code causale_versamento}. */
class CausaleUtilsTest {

	@Test
	void causaleSempliceCodificataEDecodificata() {
		String encoded = CausaleUtils.encode("Tassa rifiuti 2026");
		assertEquals("01 VGFzc2EgcmlmaXV0aSAyMDI2", encoded);
		assertEquals("Tassa rifiuti 2026", CausaleUtils.decodeSimple(encoded));
	}

	@Test
	void causaleAssente() {
		assertNull(CausaleUtils.encode(null));
		assertNull(CausaleUtils.encode("  "));
		assertNull(CausaleUtils.decodeSimple(null));
	}

	@Test
	void spezzoniDecodificatiComeIlLegacy() {
		// 02: primo spezzone; 03: "<importo>: <primo spezzone>"
		assertEquals("primo", CausaleUtils.decodeSimple("02 cHJpbW8= c2Vjb25kbw=="));
		assertEquals("10.5: primo", CausaleUtils.decodeSimple("03 cHJpbW8= MTAuNQ=="));
	}

	@Test
	void formatoNonRiconosciutoRestituitoInvariato() {
		assertEquals("Causale in chiaro", CausaleUtils.decodeSimple("Causale in chiaro"));
	}
}

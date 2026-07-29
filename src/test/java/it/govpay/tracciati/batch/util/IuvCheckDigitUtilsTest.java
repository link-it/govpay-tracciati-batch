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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IuvCheckDigitUtilsTest {

	@Test
	void checkDigit93SenzaCode() {
		// parseLong("1" + "1") = 11 ; 11 % 93 = 11
		assertEquals("11", IuvCheckDigitUtils.getCheckDigit93("1", 1));
	}

	@Test
	void checkDigit93ConCode() {
		// parseLong("0" + "05" + "1") = 51 ; 51 % 93 = 51
		assertEquals("51", IuvCheckDigitUtils.getCheckDigit93("1", 0, 5));
	}

	@Test
	void formattaIuvAuxDigit1HaLunghezza17() {
		String iuv = IuvCheckDigitUtils.formattaIuv(1, "", 123L, null, null);
		assertEquals(17, iuv.length()); // 15 reference + 2 check
	}

	@Test
	void formattaIuvAuxDigit0HaLunghezza15() {
		String iuv = IuvCheckDigitUtils.formattaIuv(0, "", 1L, 99, null);
		assertEquals(15, iuv.length()); // 13 reference + 2 check
	}

	@Test
	void formattaIuvAuxDigit3PrefissaSegregationCode() {
		String iuv = IuvCheckDigitUtils.formattaIuv(3, "", 1L, null, 2);
		assertTrue(iuv.startsWith("02"), "deve iniziare col segregation code a 2 cifre");
		assertEquals(17, iuv.length()); // 2 seg + 13 reference + 2 check
	}
}

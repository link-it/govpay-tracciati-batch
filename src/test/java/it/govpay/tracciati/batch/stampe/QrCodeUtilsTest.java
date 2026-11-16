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

import org.junit.jupiter.api.Test;

class QrCodeUtilsTest {

	@Test
	void componeQrPagoPaConImportoInCentesimi() {
		assertEquals("PAGOPA|002|301000000000000123|01234567890|12345",
				QrCodeUtils.buildQrCodePagoPa("301000000000000123", "01234567890", 123.45));
	}

	@Test
	void importoSottoDieciEuroConservaLoZeroDiRiempimento() {
		// il legacy formatta con DecimalFormat("00.00"): 5,00 euro diventa 0500, non 500
		assertEquals("PAGOPA|002|300000000000000001|00000000000|0500",
				QrCodeUtils.buildQrCodePagoPa("300000000000000001", "00000000000", 5.0));
		assertEquals("PAGOPA|002|300000000000000001|00000000000|0550",
				QrCodeUtils.buildQrCodePagoPa("300000000000000001", "00000000000", 5.5));
	}

	@Test
	void importoInteroInCentesimi() {
		assertEquals("PAGOPA|002|300000000000000001|00000000000|100000",
				QrCodeUtils.buildQrCodePagoPa("300000000000000001", "00000000000", 1000.0));
	}
}

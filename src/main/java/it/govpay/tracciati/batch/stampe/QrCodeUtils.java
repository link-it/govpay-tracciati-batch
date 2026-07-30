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

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Composizione della stringa QR pagoPA (formato 002), port di
 * {@code IuvUtils.buildQrCode002} della procedura legacy (ramo con numero avviso):
 * {@code PAGOPA|002|<numeroAvviso>|<codDominio>|<importoInCentesimi>}.
 */
public final class QrCodeUtils {

	private QrCodeUtils() {
		// utility
	}

	public static String buildQrCodePagoPa(String numeroAvviso, String codDominio, double importoTotale) {
		String centesimi = BigDecimal.valueOf(importoTotale)
				.movePointRight(2)
				.setScale(0, RoundingMode.HALF_UP)
				.toPlainString();
		return "PAGOPA|002|" + numeroAvviso + "|" + codDominio + "|" + centesimi;
	}
}

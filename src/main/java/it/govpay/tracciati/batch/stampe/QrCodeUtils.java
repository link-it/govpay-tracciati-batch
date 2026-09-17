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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Composizione della stringa QR pagoPA (formato 002), port di {@code IuvUtils.buildQrCode002} della
 * procedura legacy (ramo con numero avviso presente):
 * {@code PAGOPA|002|<numeroAvviso>|<codDominio>|<importoInCentesimi>}.
 *
 * <p>L'importo segue esattamente la formattazione legacy ({@code DecimalFormat("00.00")} con il
 * punto rimosso): la parte intera è quindi zero-padded a due cifre, per cui 5,00 € diventa
 * {@code 0500} e non {@code 500}.</p>
 */
public final class QrCodeUtils {

	private static final String PATTERN_IMPORTO = "00.00";

	private QrCodeUtils() {
		// utility
	}

	public static String buildQrCodePagoPa(String numeroAvviso, String codDominio, double importoTotale) {
		return "PAGOPA|002|" + numeroAvviso + "|" + codDominio + "|" + importoInCentesimi(importoTotale);
	}

	/** Importo in centesimi come lo scrive il legacy: {@code DecimalFormat("00.00")} senza il punto. */
	static String importoInCentesimi(double importoTotale) {
		DecimalFormat formatter = new DecimalFormat(PATTERN_IMPORTO, new DecimalFormatSymbols(Locale.ENGLISH));
		return formatter.format(importoTotale).replace(".", "");
	}
}

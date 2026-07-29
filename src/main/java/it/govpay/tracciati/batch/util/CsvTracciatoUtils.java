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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility per la gestione dei tracciati CSV (conteggio e split delle linee),
 * ricalca il comportamento di {@code it.govpay.core.utils.CSVUtils} della procedura legacy.
 */
public final class CsvTracciatoUtils {

	private CsvTracciatoUtils() {
		// utility
	}

	/**
	 * Numero totale di linee del file (intestazione inclusa). Un'eventuale newline finale
	 * non conta come linea aggiuntiva.
	 */
	public static long countLines(byte[] raw) {
		if (raw == null || raw.length == 0) {
			return 0;
		}
		String[] lines = normalizza(raw);
		return lines.length;
	}

	/**
	 * Ritorna le linee del CSV a partire dall'indice {@code skip} (0-based), utile per saltare
	 * l'intestazione e le righe già elaborate (checkpoint di ripartenza).
	 */
	public static List<String> splitCsv(byte[] raw, long skip) {
		List<String> result = new ArrayList<>();
		if (raw == null || raw.length == 0) {
			return result;
		}
		String[] lines = normalizza(raw);
		for (long i = skip; i < lines.length; i++) {
			result.add(lines[(int) i]);
		}
		return result;
	}

	private static String[] normalizza(byte[] raw) {
		String content = new String(raw, StandardCharsets.UTF_8);
		String[] lines = content.split("\r?\n", -1);
		// scarta l'ultima linea vuota dovuta a newline finale
		int n = lines.length;
		if (n > 0 && lines[n - 1].isEmpty()) {
			String[] trimmed = new String[n - 1];
			System.arraycopy(lines, 0, trimmed, 0, n - 1);
			return trimmed;
		}
		return lines;
	}
}

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

/**
 * Calcolo del check-digit e formattazione dell'IUV secondo l'algoritmo pagoPA.
 *
 * <p>Port fedele di {@code it.govpay.bd.pagamento.util.IuvUtils} e della logica di formattazione
 * di {@code IuvBD.generaIuv} (esclusa la generazione del progressivo {@code prg}, che è
 * demandata a un servizio con stato su DB). Contiene solo logica pura, verificabile con unit test.</p>
 */
public final class IuvCheckDigitUtils {

	private IuvCheckDigitUtils() {
		// utility
	}

	/** Check-digit modulo 93 con application/segregation code (auxDigit 0 e 3). */
	public static String getCheckDigit93(String reference, int auxDigit, int code) {
		long resto93 = Long.parseLong(auxDigit + String.format("%02d", code) + reference) % 93;
		return String.format("%02d", resto93);
	}

	/** Check-digit modulo 93 senza code (auxDigit 1 e 2). */
	public static String getCheckDigit93(String reference, int auxDigit) {
		long resto93 = Long.parseLong(auxDigit + reference) % 93;
		return String.format("%02d", resto93);
	}

	/** Check-digit ISO 11640 (mod 97) usato per i riferimenti RF. */
	public static String getCheckDigitIso11640(String reference) {
		StringBuilder sb = new StringBuilder();
		String ref = reference.toUpperCase();
		for (int i = 0; i < ref.length(); i++) {
			char c = ref.charAt(i);
			if (c >= '0' && c <= '9') {
				sb.append(c);
			} else if (c >= 'A' && c <= 'Z') {
				sb.append(c - 'A' + 10);
			} else {
				throw new IllegalArgumentException("Carattere [" + c + "] non ammesso nell'IUV");
			}
		}
		java.math.BigInteger base = new java.math.BigInteger(sb + "271500");
		int diff98 = 98 - base.mod(java.math.BigInteger.valueOf(97)).intValue();
		return String.format("%02d", diff98);
	}

	/**
	 * Costruisce l'IUV a partire dal progressivo, replicando lo switch su {@code auxDigit} di
	 * {@code IuvBD.generaIuv}.
	 *
	 * @param auxDigit        aux digit del dominio (0, 1, 2, 3)
	 * @param prefix          prefisso IUV (eventualmente vuoto)
	 * @param prg             progressivo generato
	 * @param applicationCode application code della stazione (usato per auxDigit 0)
	 * @param segregationCode segregation code del dominio (usato per auxDigit 3)
	 * @return l'IUV formattato con check-digit
	 */
	public static String formattaIuv(int auxDigit, String prefix, long prg, Integer applicationCode, Integer segregationCode) {
		String p = prefix != null ? prefix : "";
		switch (auxDigit) {
		case 0: {
			String reference = p + String.format("%0" + (13 - p.length()) + "d", prg);
			verificaLunghezza(reference);
			if (applicationCode == null) {
				throw new IllegalArgumentException("Application code assente per IUV con auxDigit 0");
			}
			return reference + getCheckDigit93(reference, auxDigit, applicationCode);
		}
		case 1:
		case 2: {
			String reference = p + String.format("%0" + (15 - p.length()) + "d", prg);
			verificaLunghezza(reference);
			return reference + getCheckDigit93(reference, auxDigit);
		}
		case 3: {
			String reference = p + String.format("%0" + (13 - p.length()) + "d", prg);
			verificaLunghezza(reference);
			if (segregationCode == null) {
				throw new IllegalArgumentException("Segregation code assente per IUV con auxDigit 3");
			}
			return String.format("%02d", segregationCode) + reference + getCheckDigit93(reference, auxDigit, segregationCode);
		}
		default:
			throw new IllegalArgumentException("Codice AUX non supportato: " + auxDigit);
		}
	}

	private static void verificaLunghezza(String reference) {
		if (reference.length() > 15) {
			throw new IllegalStateException("Superato il numero massimo di IUV generabili per il prefisso");
		}
	}
}

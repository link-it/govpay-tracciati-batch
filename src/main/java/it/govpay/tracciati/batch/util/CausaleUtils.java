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
import java.util.Base64;

/**
 * Codifica/decodifica della causale di una posizione debitoria: su GovPay la colonna
 * {@code versamenti.causale_versamento} non contiene il testo in chiaro ma la forma codificata
 * usata da {@code it.govpay.model.Versamento.encode()/decode()}:
 * <ul>
 *   <li>{@code 01 <base64>} — causale semplice;</li>
 *   <li>{@code 02 <base64> <base64> …} — spezzoni;</li>
 *   <li>{@code 03 <base64> <base64importo> …} — spezzoni strutturati.</li>
 * </ul>
 *
 * <p>Il batch <b>scrive</b> sempre in forma di causale semplice ({@code 01}), mentre in
 * <b>lettura</b> gestisce anche gli altri formati (dati caricati da altri canali), restituendo la
 * stessa rappresentazione sintetica di {@code Causale.getSimple()}.</p>
 */
public final class CausaleUtils {

	private static final String PREFISSO_SEMPLICE = "01";
	private static final String PREFISSO_SPEZZONI = "02";
	private static final String PREFISSO_SPEZZONI_STRUTTURATI = "03";

	private CausaleUtils() {
		// utility
	}

	/** Codifica il testo come causale semplice ({@code 01 <base64>}); {@code null} se assente. */
	public static String encode(String causale) {
		if (causale == null || causale.isBlank()) {
			return null;
		}
		return PREFISSO_SEMPLICE + " " + Base64.getEncoder().encodeToString(causale.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Testo della causale a partire dalla forma codificata. Se la stringa non è in un formato noto
	 * viene restituita così com'è (tolleranza verso dati storici non codificati).
	 */
	public static String decodeSimple(String encoded) {
		if (encoded == null || encoded.isBlank()) {
			return null;
		}
		String[] parti = encoded.trim().split(" ");
		switch (parti[0]) {
		case PREFISSO_SEMPLICE:
			return parti.length > 1 ? decodifica(parti[1]) : null;
		case PREFISSO_SPEZZONI:
			// come Causale.getSimple(): il primo spezzone
			return parti.length > 1 ? decodifica(parti[1]) : "";
		case PREFISSO_SPEZZONI_STRUTTURATI:
			// come Causale.getSimple(): "<importo>: <primo spezzone>"
			if (parti.length > 2) {
				return decodifica(parti[2]) + ": " + decodifica(parti[1]);
			}
			return "";
		default:
			return encoded;
		}
	}

	private static String decodifica(String base64) {
		try {
			return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return base64;
		}
	}
}

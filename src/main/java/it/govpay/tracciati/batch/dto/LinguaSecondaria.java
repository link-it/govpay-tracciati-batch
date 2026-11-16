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
package it.govpay.tracciati.batch.dto;

/**
 * Seconda lingua dell'avviso di pagamento, come indicata in {@code versamenti.proprieta}
 * (port di {@code it.govpay.core.beans.tracciati.LinguaSecondaria}). Il valore {@code FALSE}
 * disabilita esplicitamente il bilinguismo.
 */
public enum LinguaSecondaria {

	FALSE("false"),
	DE("de"),
	EN("en"),
	FR("fr"),
	SL("sl");

	private final String valore;

	LinguaSecondaria(String valore) {
		this.valore = valore;
	}

	public String getValore() {
		return this.valore;
	}

	/** Bilinguismo effettivo: {@code false} e {@code null} non producono avvisi bilingue. */
	public boolean isBilingue() {
		return this != FALSE;
	}

	/** Enum corrispondente al valore JSON, {@code null} se assente o non riconosciuto. */
	public static LinguaSecondaria da(String valore) {
		if (valore == null || valore.isBlank()) {
			return null;
		}
		for (LinguaSecondaria lingua : values()) {
			if (lingua.valore.equalsIgnoreCase(valore.trim())) {
				return lingua;
			}
		}
		return null;
	}
}

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

import it.govpay.tracciati.batch.entity.TipoSogliaVersamento;
import it.govpay.tracciati.batch.entity.Versamento;

/**
 * Decodifica di {@code versamenti.cod_rata}, che nel modello GovPay non è una semplice etichetta ma
 * codifica il ruolo della posizione all'interno del documento (port di
 * {@code it.govpay.bd.model.converter.VersamentoConverter}):
 * <ul>
 *   <li>{@code ENTRO<giorni>} / {@code OLTRE<giorni>} → soglia + numero di giorni;</li>
 *   <li>{@code RIDOTTO} / {@code SCONTATO} → soglia di violazione CDS;</li>
 *   <li>valore numerico → numero di rata;</li>
 *   <li>{@code null} → rata unica.</li>
 * </ul>
 */
public final class CodRataUtils {

	private CodRataUtils() {
		// utility
	}

	/**
	 * Ruolo della posizione debitoria nel documento: rata unica (tutto {@code null}), rata numerata
	 * oppure soglia (con i giorni, per ENTRO/OLTRE).
	 */
	public record RataSoglia(Integer numeroRata, TipoSogliaVersamento tipoSoglia, Integer giorniSoglia) {

		private static final RataSoglia RATA_UNICA = new RataSoglia(null, null, null);

		/** Rata unica: né numero di rata né soglia (è la posizione pagabile in un'unica soluzione). */
		public boolean isRataUnica() {
			return this.numeroRata == null && this.tipoSoglia == null;
		}

		/** Soglia di violazione al Codice della Strada (importo ridotto o scontato). */
		public boolean isViolazioneCds() {
			return this.tipoSoglia == TipoSogliaVersamento.RIDOTTO || this.tipoSoglia == TipoSogliaVersamento.SCONTATO;
		}

		/** Soglia temporale (pagamento entro/oltre N giorni). */
		public boolean isSogliaTemporale() {
			return this.tipoSoglia == TipoSogliaVersamento.ENTRO || this.tipoSoglia == TipoSogliaVersamento.OLTRE;
		}
	}

	public static RataSoglia da(Versamento versamento) {
		return decodifica(versamento.getCodRata());
	}

	public static RataSoglia decodifica(String codRata) {
		if (codRata == null || codRata.isBlank()) {
			return RataSoglia.RATA_UNICA;
		}
		String valore = codRata.trim();
		for (TipoSogliaVersamento tipo : new TipoSogliaVersamento[] {TipoSogliaVersamento.ENTRO, TipoSogliaVersamento.OLTRE}) {
			if (valore.startsWith(tipo.name())) {
				return new RataSoglia(null, tipo, giorni(valore.substring(tipo.name().length())));
			}
		}
		if (valore.startsWith(TipoSogliaVersamento.RIDOTTO.name())) {
			return new RataSoglia(null, TipoSogliaVersamento.RIDOTTO, null);
		}
		if (valore.startsWith(TipoSogliaVersamento.SCONTATO.name())) {
			return new RataSoglia(null, TipoSogliaVersamento.SCONTATO, null);
		}
		return new RataSoglia(numeroRata(valore), null, null);
	}

	private static Integer giorni(String valore) {
		try {
			return Integer.valueOf(valore.trim());
		} catch (NumberFormatException e) {
			// il legacy propaga l'errore di parsing: qui la soglia resta senza giorni
			return null;
		}
	}

	private static Integer numeroRata(String valore) {
		try {
			return Integer.valueOf(valore);
		} catch (NumberFormatException e) {
			// cod_rata non numerico e senza prefisso noto: trattato come rata unica
			return null;
		}
	}
}

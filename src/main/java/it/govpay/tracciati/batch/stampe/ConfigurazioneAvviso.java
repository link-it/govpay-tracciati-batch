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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import it.govpay.tracciati.batch.entity.TipoSogliaVersamento;
import it.govpay.tracciati.batch.entity.Versamento;
import it.govpay.tracciati.batch.util.CodRataUtils;
import it.govpay.tracciati.batch.util.CodRataUtils.RataSoglia;

/**
 * Classificazione delle posizioni debitorie di un avviso, port di
 * {@code it.govpay.core.utils.stampe.AvvisoPagamentoInputConf}: decide se l'avviso è un bollettino
 * postale, se è una violazione al Codice della Strada e come vanno ripartiti gli importi
 * (rata unica, rate, pagamenti in forma ridotta).
 *
 * <p>Il <b>layout</b> delle pagine non è più calcolato qui: lo determina il microservizio di stampa
 * a partire da questa ripartizione, quindi della vecchia utility si porta solo la parte semantica.</p>
 */
public record ConfigurazioneAvviso(
		boolean postale,
		boolean violazioneCds,
		List<Versamento> rateUniche,
		List<Versamento> rate,
		List<Versamento> soglieTemporali,
		Versamento ridotto,
		Versamento scontato) {

	/**
	 * @param versamenti     posizioni dell'avviso
	 * @param conIbanPostale id delle posizioni per cui è stato risolto un IBAN postale
	 */
	public static ConfigurazioneAvviso da(List<Versamento> versamenti, Set<Long> conIbanPostale) {
		List<Versamento> rateUniche = new ArrayList<>();
		List<Versamento> rate = new ArrayList<>();
		List<Versamento> soglieTemporali = new ArrayList<>();
		Versamento ridotto = null;
		Versamento scontato = null;
		int numeroSoglieCds = 0;

		for (Versamento versamento : versamenti) {
			RataSoglia rataSoglia = CodRataUtils.da(versamento);
			if (rataSoglia.isRataUnica()) {
				rateUniche.add(versamento);
			} else if (rataSoglia.numeroRata() != null) {
				rate.add(versamento);
			} else if (rataSoglia.isSogliaTemporale()) {
				soglieTemporali.add(versamento);
			} else if (rataSoglia.isViolazioneCds()) {
				numeroSoglieCds++;
				if (rataSoglia.tipoSoglia() == TipoSogliaVersamento.RIDOTTO && ridotto == null) {
					ridotto = versamento;
				} else if (rataSoglia.tipoSoglia() == TipoSogliaVersamento.SCONTATO && scontato == null) {
					scontato = versamento;
				}
			}
		}

		// come nel legacy: l'avviso è postale solo se lo sono tutte le posizioni
		boolean postale = !versamenti.isEmpty() && conIbanPostale.size() == versamenti.size();
		// come nel legacy: violazione CDS solo se tutte le posizioni sono ridotto/scontato
		boolean violazioneCds = numeroSoglieCds == versamenti.size() && ridotto != null && scontato != null;

		rate.sort(Comparator.comparing(v -> numeroRata(v), Comparator.nullsLast(Comparator.naturalOrder())));
		soglieTemporali.sort(Comparator.comparing(v -> giorniSoglia(v), Comparator.nullsLast(Comparator.naturalOrder())));

		return new ConfigurazioneAvviso(postale, violazioneCds, List.copyOf(rateUniche), List.copyOf(rate),
				List.copyOf(soglieTemporali), ridotto, scontato);
	}

	private static Integer numeroRata(Versamento versamento) {
		return CodRataUtils.da(versamento).numeroRata();
	}

	private static Integer giorniSoglia(Versamento versamento) {
		return CodRataUtils.da(versamento).giorniSoglia();
	}

	/** Nessun importo classificato: non c'è avviso da produrre. */
	public boolean isVuota() {
		return this.rateUniche.isEmpty() && this.rate.isEmpty() && this.soglieTemporali.isEmpty()
				&& this.ridotto == null && this.scontato == null;
	}
}

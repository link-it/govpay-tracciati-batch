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

import java.util.List;

import it.govpay.tracciati.batch.entity.Documento;
import it.govpay.tracciati.batch.entity.Versamento;

/**
 * Unità di stampa: un avviso PDF corrisponde a un <b>documento</b> con tutte le sue rate caricate
 * dal tracciato, oppure a una singola posizione debitoria senza documento.
 *
 * <p>È il raggruppamento che il vecchio flusso otteneva stampando l'intero documento per ogni
 * versamento e deduplicando poi i PDF nello ZIP.</p>
 */
public record AvvisoDaStampare(Documento documento, List<Versamento> versamenti) {

	public static AvvisoDaStampare diVersamento(Versamento versamento) {
		return new AvvisoDaStampare(null, List.of(versamento));
	}

	public static AvvisoDaStampare diDocumento(Documento documento, List<Versamento> versamenti) {
		return new AvvisoDaStampare(documento, versamenti);
	}

	/** Posizione da cui si ricavano i dati comuni dell'avviso (debitore, dominio, proprietà). */
	public Versamento versamentoPrincipale() {
		return this.versamenti.get(0);
	}

	public String numeroDocumento() {
		return this.documento != null ? this.documento.getCodDocumento() : null;
	}
}

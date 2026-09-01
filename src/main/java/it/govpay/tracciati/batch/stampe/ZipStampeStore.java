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

import java.io.IOException;
import java.io.OutputStream;

/**
 * Persistenza dello ZIP degli avvisi di un tracciato sulla colonna {@code tracciati.zip_stampe}.
 *
 * <p>Riprende l'ottimizzazione della procedura legacy: lo ZIP <b>non viene materializzato in
 * memoria</b> ma prodotto direttamente sullo stream della destinazione binaria del database
 * (Large Object PostgreSQL o {@code Blob} JDBC), così che un tracciato con molte migliaia di avvisi
 * non richieda un buffer di dimensione pari all'intero archivio.</p>
 */
public interface ZipStampeStore {

	/**
	 * Produttore dello ZIP: riceve lo stream della destinazione sul database e vi scrive l'archivio.
	 * Non deve chiudere lo stream ricevuto (la chiusura è a carico dello store).
	 */
	@FunctionalInterface
	interface ProduttoreZip {
		void scrivi(OutputStream destinazione) throws IOException;
	}

	/**
	 * Scrive lo ZIP delle stampe del tracciato indicato su {@code tracciati.zip_stampe}.
	 *
	 * @param idTracciato id del tracciato da aggiornare
	 * @param produttore  produttore dell'archivio, invocato sullo stream della destinazione
	 * @return numero di byte scritti
	 */
	long scrivi(long idTracciato, ProduttoreZip produttore);
}

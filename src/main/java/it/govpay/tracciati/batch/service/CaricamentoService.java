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
package it.govpay.tracciati.batch.service;

import it.govpay.tracciati.batch.dto.AnnullamentoPendenza;
import it.govpay.tracciati.batch.dto.EsitoCaricamento;
import it.govpay.tracciati.batch.dto.PendenzaPost;
import it.govpay.tracciati.batch.entity.Tracciato;

/**
 * Caricamento/annullamento di una posizione debitoria sul database GovPay.
 *
 * <p><b>Seam architetturale (Punto 5c):</b> l'implementazione concreta deve:
 * risolvere le anagrafiche (dominio, applicazione, tipo versamento dominio),
 * generare IUV e numero avviso (progressivo pagoPA — vedi {@link IuvProgressivoService}),
 * inserire {@code versamenti} + {@code singoli_versamenti} (+ eventuale {@code documenti})
 * e ritornare l'esito. Richiede un DB GovPay reale per l'implementazione e la validazione;
 * è la parte più consistente di R1.</p>
 */
public interface CaricamentoService {

	EsitoCaricamento caricaPendenza(PendenzaPost pendenza, Tracciato tracciato);

	EsitoCaricamento annullaPendenza(AnnullamentoPendenza annullamento, Tracciato tracciato);
}

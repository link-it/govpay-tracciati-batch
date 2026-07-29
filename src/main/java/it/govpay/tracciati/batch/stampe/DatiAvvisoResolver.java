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

import it.govpay.tracciati.batch.entity.Versamento;

/**
 * Risolve i dati dell'avviso non presenti sulla posizione debitoria: ente creditore (ragione
 * sociale, codice fiscale, loghi), IBAN e stringa QR pagoPA.
 *
 * <p><b>Seam architetturale (Punto 8):</b> l'implementazione concreta legge il dominio
 * ({@code DominioEntity} di govpay-common) e compone il QR pagoPA dall'IUV/numero avviso.
 * Richiede l'anagrafica del dominio e la logica di composizione QR; è la parte da completare
 * insieme a {@code CaricamentoService}.</p>
 */
public interface DatiAvvisoResolver {

	DatiAvvisoCreditore risolvi(Versamento versamento);
}

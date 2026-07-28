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

import java.util.List;

import lombok.Data;

/** Esito complessivo dell'elaborazione di un tracciato (serializzato in {@code tracciati.raw_esito} per il formato JSON). */
@Data
public class EsitoTracciatoPendenze {
	private String idTracciato;
	private List<EsitoOperazionePendenza> inserimenti;
	private List<EsitoOperazionePendenza> annullamenti;
}

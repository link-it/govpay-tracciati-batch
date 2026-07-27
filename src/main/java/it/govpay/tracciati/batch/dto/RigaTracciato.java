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

import it.govpay.tracciati.batch.entity.TipoOperazione;
import lombok.Data;

/**
 * Item elaborato dal batch: una singola operazione (linea) di un tracciato.
 * È l'unità che attraversa reader → processor → writer dello step di caricamento.
 */
@Data
public class RigaTracciato {

	/** Numero di linea (1-based) coerente con la numerazione della procedura legacy. */
	private long linea;

	/** ADD (inserimento) o DEL (annullamento). */
	private TipoOperazione tipoOperazione;

	/** Payload originale della richiesta in JSON (persistito in operazioni.dati_richiesta). */
	private String jsonRichiesta;

	/** Pendenza da inserire (valorizzata per le operazioni ADD). */
	private PendenzaPost pendenza;

	/** Annullamento (valorizzato per le operazioni DEL). */
	private AnnullamentoPendenza annullamento;

	/** Messaggio di errore di validazione, se la riga non è valida (null = valida). */
	private String errore;

	public boolean isValida() {
		return this.errore == null;
	}
}

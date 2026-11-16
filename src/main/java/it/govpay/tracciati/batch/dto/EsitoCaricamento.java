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

import it.govpay.tracciati.batch.entity.StatoOperazione;
import lombok.Builder;
import lombok.Data;

/** Esito del caricamento/annullamento di una singola pendenza. */
@Data
@Builder
public class EsitoCaricamento {

	private StatoOperazione stato;
	private String dettaglioEsito;
	private Long idVersamento;
	/**
	 * Riferimento a {@code stampe} per {@code operazioni.id_stampa}: resta null, come nel legacy, da
	 * quando l'avviso del tracciato non viene più salvato su DB ma solo nello ZIP (issue #262).
	 */
	private Long idStampa;
	private String iuv;
	private String numeroAvviso;
	private String codVersamentoEnte;
	private Long idApplicazione;

	public static EsitoCaricamento ko(String dettaglio) {
		return EsitoCaricamento.builder()
				.stato(StatoOperazione.ESEGUITO_KO)
				.dettaglioEsito(dettaglio)
				.build();
	}
}

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
package it.govpay.tracciati.batch.entity;

/**
 * Tipo di soglia di una posizione debitoria, codificato nel prefisso di {@code versamenti.cod_rata}
 * (port di {@code it.govpay.model.Versamento.TipoSogliaVersamento}):
 * <ul>
 *   <li>{@code ENTRO}/{@code OLTRE}: pagamento ridotto entro/oltre N giorni (seguiti dai giorni);</li>
 *   <li>{@code RIDOTTO}/{@code SCONTATO}: importi di una violazione al Codice della Strada.</li>
 * </ul>
 */
public enum TipoSogliaVersamento {

	ENTRO,
	OLTRE,
	SCONTATO,
	RIDOTTO
}

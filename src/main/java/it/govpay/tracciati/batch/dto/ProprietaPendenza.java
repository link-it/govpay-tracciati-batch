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

import lombok.Data;

/**
 * Sottoinsieme di {@code versamenti.proprieta} (JSON) che influenza la stampa dell'avviso
 * (port di {@code it.govpay.core.beans.tracciati.ProprietaPendenza}): seconda lingua con la
 * relativa causale e data di scadenza da riportare sull'avviso.
 *
 * <p>Il nome {@code dataScandenzaAvviso} contiene il refuso presente nel modello GovPay: va
 * mantenuto perché è la chiave effettivamente serializzata nella colonna.</p>
 */
@Data
public class ProprietaPendenza {

	private String linguaSecondaria;
	private String linguaSecondariaCausale;
	private String dataScandenzaAvviso;
	private String informativaImportoAvviso;
	private String linguaSecondariaInformativaImportoAvviso;

	public LinguaSecondaria getLinguaSecondariaEnum() {
		return LinguaSecondaria.da(this.linguaSecondaria);
	}
}

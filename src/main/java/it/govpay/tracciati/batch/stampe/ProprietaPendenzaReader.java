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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import it.govpay.tracciati.batch.dto.ProprietaPendenza;
import it.govpay.tracciati.batch.entity.Versamento;
import tools.jackson.databind.ObjectMapper;

/**
 * Legge le proprietà di stampa dalla colonna JSON {@code versamenti.proprieta} (seconda lingua,
 * causale tradotta, data di scadenza da riportare sull'avviso).
 *
 * <p>Un JSON illeggibile non fa fallire la stampa: si procede senza personalizzazioni, come farebbe
 * la vecchia procedura in assenza di proprietà.</p>
 */
@Component
public class ProprietaPendenzaReader {

	private static final Logger log = LoggerFactory.getLogger(ProprietaPendenzaReader.class);

	private final ObjectMapper objectMapper;

	public ProprietaPendenzaReader(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	/** Proprietà della posizione; mai {@code null}. */
	public ProprietaPendenza leggi(Versamento versamento) {
		String json = versamento.getProprieta();
		if (json == null || json.isBlank()) {
			return new ProprietaPendenza();
		}
		try {
			ProprietaPendenza proprieta = this.objectMapper.readValue(json, ProprietaPendenza.class);
			return proprieta != null ? proprieta : new ProprietaPendenza();
		} catch (Exception e) {
			log.warn("Proprietà della posizione {} non interpretabili, avviso senza personalizzazioni: {}",
					versamento.getCodVersamentoEnte(), e.getMessage());
			return new ProprietaPendenza();
		}
	}
}

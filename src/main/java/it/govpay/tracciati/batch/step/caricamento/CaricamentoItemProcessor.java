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
package it.govpay.tracciati.batch.step.caricamento;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import it.govpay.tracciati.batch.dto.PendenzaPost;
import it.govpay.tracciati.batch.dto.RigaTracciato;

/**
 * Validazione di base di una riga del tracciato prima del caricamento.
 *
 * <p>La riga viene sempre propagata al writer: se non valida, viene marcata con un messaggio di
 * errore ({@link RigaTracciato#getErrore()}) e il writer registrerà l'operazione con esito KO
 * (coerentemente con la procedura legacy, che prosegue accumulando gli esiti).</p>
 *
 * <p>Nota: la validazione applicativa completa della pendenza (equivalente a
 * {@code PendenzaPostValidator}) sarà integrata insieme alla logica di caricamento (Punto 5c).</p>
 */
@Component
public class CaricamentoItemProcessor implements ItemProcessor<RigaTracciato, RigaTracciato> {

	@Override
	public RigaTracciato process(RigaTracciato item) {
		switch (item.getTipoOperazione()) {
		case ADD:
			validaInserimento(item);
			break;
		case DEL:
			validaAnnullamento(item);
			break;
		default:
			item.setErrore("Tipo operazione non supportato: " + item.getTipoOperazione());
			break;
		}
		return item;
	}

	private void validaInserimento(RigaTracciato item) {
		PendenzaPost pendenza = item.getPendenza();
		if (pendenza == null) {
			item.setErrore("Pendenza assente");
			return;
		}
		if (isBlank(pendenza.getIdPendenza())) {
			item.setErrore("idPendenza obbligatorio");
			return;
		}
		boolean importoAssente = pendenza.getImporto() == null;
		boolean vociAssenti = pendenza.getVoci() == null || pendenza.getVoci().isEmpty();
		if (importoAssente && vociAssenti) {
			item.setErrore("Indicare l'importo della pendenza o almeno una voce");
		}
	}

	private void validaAnnullamento(RigaTracciato item) {
		if (item.getAnnullamento() == null) {
			item.setErrore("Annullamento assente");
			return;
		}
		if (isBlank(item.getAnnullamento().getIdPendenza())) {
			item.setErrore("idPendenza obbligatorio per l'annullamento");
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}
}

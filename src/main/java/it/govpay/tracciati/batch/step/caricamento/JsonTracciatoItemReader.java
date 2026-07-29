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

import java.util.ArrayList;
import java.util.List;

import org.springframework.batch.infrastructure.item.ItemReader;

import it.govpay.tracciati.batch.dto.AnnullamentoPendenza;
import it.govpay.tracciati.batch.dto.PendenzaPost;
import it.govpay.tracciati.batch.dto.RigaTracciato;
import it.govpay.tracciati.batch.dto.TracciatoPendenza;
import it.govpay.tracciati.batch.dto.TracciatoPendenzePost;
import it.govpay.tracciati.batch.entity.TipoOperazione;
import it.govpay.tracciati.batch.entity.Tracciato;
import tools.jackson.databind.ObjectMapper;

/**
 * Reader di un tracciato in formato JSON: produce le righe (ADD per gli inserimenti, DEL per
 * gli annullamenti) saltando quelle già elaborate secondo i checkpoint del {@code bean_dati}.
 * <p>La numerazione delle linee ricalca la procedura legacy: gli ADD hanno {@code linea = indice+1},
 * i DEL proseguono con {@code linea = numAddTotali + indice + 1}.</p>
 */
public class JsonTracciatoItemReader implements ItemReader<RigaTracciato> {

	private final ObjectMapper objectMapper;
	private final List<RigaTracciato> righe = new ArrayList<>();
	private int cursor = 0;

	public JsonTracciatoItemReader(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	/**
	 * Prepara la lettura del tracciato: parsa il contenuto JSON e costruisce la coda delle righe
	 * da elaborare a partire dai checkpoint.
	 */
	public void bind(Tracciato tracciato, TracciatoPendenza beanDati) {
		this.righe.clear();
		this.cursor = 0;

		TracciatoPendenzePost contenuto = this.objectMapper.readValue(tracciato.getRawRichiesta(), TracciatoPendenzePost.class);
		String codDominio = tracciato.getCodDominio();

		List<PendenzaPost> inserimenti = contenuto.getInserimenti();
		int numAdd = inserimenti != null ? inserimenti.size() : 0;
		for (int i = (int) beanDati.getLineaElaborazioneAdd(); i < numAdd; i++) {
			PendenzaPost pendenza = inserimenti.get(i);
			pendenza.setIdDominio(codDominio);
			RigaTracciato riga = new RigaTracciato();
			riga.setLinea(i + 1L);
			riga.setTipoOperazione(TipoOperazione.ADD);
			riga.setPendenza(pendenza);
			riga.setJsonRichiesta(this.objectMapper.writeValueAsString(pendenza));
			this.righe.add(riga);
		}

		List<AnnullamentoPendenza> annullamenti = contenuto.getAnnullamenti();
		int numDel = annullamenti != null ? annullamenti.size() : 0;
		for (int j = (int) beanDati.getLineaElaborazioneDel(); j < numDel; j++) {
			AnnullamentoPendenza annullamento = annullamenti.get(j);
			RigaTracciato riga = new RigaTracciato();
			riga.setLinea((long) numAdd + j + 1);
			riga.setTipoOperazione(TipoOperazione.DEL);
			riga.setAnnullamento(annullamento);
			riga.setJsonRichiesta(this.objectMapper.writeValueAsString(annullamento));
			this.righe.add(riga);
		}
	}

	@Override
	public synchronized RigaTracciato read() {
		if (this.cursor >= this.righe.size()) {
			return null;
		}
		return this.righe.get(this.cursor++);
	}
}

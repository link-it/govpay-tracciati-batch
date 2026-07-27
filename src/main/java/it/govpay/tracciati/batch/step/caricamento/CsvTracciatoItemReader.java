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

import java.util.List;

import org.springframework.batch.infrastructure.item.ItemReader;

import it.govpay.tracciati.batch.dto.PendenzaPost;
import it.govpay.tracciati.batch.dto.RigaTracciato;
import it.govpay.tracciati.batch.dto.TracciatoPendenza;
import it.govpay.tracciati.batch.entity.TipoOperazione;
import it.govpay.tracciati.batch.entity.Tracciato;
import it.govpay.tracciati.batch.service.TrasformazioneCsvService;
import it.govpay.tracciati.batch.util.CsvTracciatoUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * Reader di un tracciato in formato CSV: per ogni riga (saltate intestazione e righe già elaborate)
 * applica il template FreeMarker di richiesta per ottenere il JSON della pendenza e lo parsa in
 * {@link PendenzaPost}. Il CSV gestisce solo operazioni di inserimento (ADD).
 */
public class CsvTracciatoItemReader implements ItemReader<RigaTracciato> {

	private final ObjectMapper objectMapper;
	private final TrasformazioneCsvService trasformazioneCsvService;

	private List<String> righeCsv;
	private long lineaIniziale;
	private String templateRichiesta;
	private String codDominio;
	private String codTipoVersamento;
	private int cursor = 0;

	public CsvTracciatoItemReader(ObjectMapper objectMapper, TrasformazioneCsvService trasformazioneCsvService) {
		this.objectMapper = objectMapper;
		this.trasformazioneCsvService = trasformazioneCsvService;
	}

	/**
	 * Prepara la lettura: splitta il CSV a partire dalla linea di checkpoint e memorizza il template
	 * di trasformazione risolto per il tipo versamento/dominio.
	 */
	public void bind(Tracciato tracciato, TracciatoPendenza beanDati, String templateRichiesta) {
		this.lineaIniziale = beanDati.getLineaElaborazioneAdd();
		this.righeCsv = CsvTracciatoUtils.splitCsv(tracciato.getRawRichiesta(), this.lineaIniziale);
		this.templateRichiesta = templateRichiesta;
		this.codDominio = tracciato.getCodDominio();
		this.codTipoVersamento = tracciato.getCodTipoVersamento();
		this.cursor = 0;
	}

	@Override
	public RigaTracciato read() {
		if (this.righeCsv == null || this.cursor >= this.righeCsv.size()) {
			return null;
		}
		String rigaCsv = this.righeCsv.get(this.cursor);
		long linea = this.lineaIniziale + this.cursor;
		this.cursor++;

		String jsonPendenza = this.trasformazioneCsvService.trasformaRigaCsv(this.templateRichiesta, rigaCsv, this.codDominio, this.codTipoVersamento);
		PendenzaPost pendenza = this.objectMapper.readValue(jsonPendenza, PendenzaPost.class);
		pendenza.setIdDominio(this.codDominio);

		RigaTracciato riga = new RigaTracciato();
		riga.setLinea(linea);
		riga.setTipoOperazione(TipoOperazione.ADD);
		riga.setPendenza(pendenza);
		riga.setJsonRichiesta(jsonPendenza);
		return riga;
	}
}

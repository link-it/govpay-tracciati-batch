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
package it.govpay.tracciati.batch.service;

import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import it.govpay.tracciati.batch.dto.EsitoCsvTemplate;
import it.govpay.tracciati.batch.dto.EsitoOperazionePendenza;
import it.govpay.tracciati.batch.dto.EsitoTracciatoPendenze;
import it.govpay.tracciati.batch.entity.Operazione;
import it.govpay.tracciati.batch.entity.TipoOperazione;
import it.govpay.tracciati.batch.entity.Tracciato;
import it.govpay.tracciati.batch.repository.OperazioneRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Produzione dell'esito dell'elaborazione di un tracciato, a partire dalle {@link Operazione}
 * registrate. Formato JSON ({@link EsitoTracciatoPendenze}) o CSV (template FreeMarker di risposta).
 * Imposta {@code raw_esito} e {@code file_name_esito} sul tracciato.
 *
 * <p>Nota (enrichment): l'esito CSV espone al template le variabili disponibili dalle operazioni
 * ({@code headerRisposta}, {@code idDominio}, {@code idTipoVersamento}, {@code esitoOperazione},
 * {@code descrizioneEsitoOperazione}, {@code tipoOperazione}). Le variabili {@code versamento},
 * {@code documento}, {@code dominio}, {@code applicazione} — presenti nel legacy — richiedono
 * lookup aggiuntivi e saranno aggiunte insieme a CaricamentoService.</p>
 */
@Service
public class ProduzioneEsitoService {

	private static final int PAGE_SIZE = 500;

	private final OperazioneRepository operazioneRepository;
	private final ObjectMapper objectMapper;
	private final TrasformazioneCsvService trasformazioneCsvService;

	public ProduzioneEsitoService(OperazioneRepository operazioneRepository, ObjectMapper objectMapper,
			TrasformazioneCsvService trasformazioneCsvService) {
		this.operazioneRepository = operazioneRepository;
		this.objectMapper = objectMapper;
		this.trasformazioneCsvService = trasformazioneCsvService;
	}

	/**
	 * Produce l'esito del tracciato e lo imposta su {@code raw_esito}/{@code file_name_esito}.
	 *
	 * @param tracciato   tracciato elaborato
	 * @param templateCsv template CSV di risposta (necessario solo per formato CSV, altrimenti null)
	 */
	public void produciEsito(Tracciato tracciato, EsitoCsvTemplate templateCsv) {
		byte[] raw;
		switch (tracciato.getFormato()) {
		case JSON:
			raw = esitoJson(tracciato);
			break;
		case CSV:
			raw = esitoCsv(tracciato, templateCsv);
			break;
		case XML:
		default:
			throw new UnsupportedOperationException("Formato esito non supportato: " + tracciato.getFormato());
		}
		tracciato.setRawEsito(raw);
		tracciato.setFileNameEsito(MessageFormat.format("esito_{0}", tracciato.getFileNameRichiesta()));
	}

	private byte[] esitoJson(Tracciato tracciato) {
		EsitoTracciatoPendenze esito = new EsitoTracciatoPendenze();
		esito.setIdTracciato(tracciato.getFileNameRichiesta());
		List<EsitoOperazionePendenza> inserimenti = new ArrayList<>();
		List<EsitoOperazionePendenza> annullamenti = new ArrayList<>();
		forEachOperazione(tracciato.getId(), op -> {
			EsitoOperazionePendenza e = toEsitoOperazione(op);
			if (op.getTipoOperazione() == TipoOperazione.DEL) {
				annullamenti.add(e);
			} else if (op.getTipoOperazione() == TipoOperazione.ADD) {
				inserimenti.add(e);
			}
		});
		esito.setInserimenti(inserimenti);
		esito.setAnnullamenti(annullamenti);
		return this.objectMapper.writeValueAsString(esito).getBytes(StandardCharsets.UTF_8);
	}

	private byte[] esitoCsv(Tracciato tracciato, EsitoCsvTemplate templateCsv) {
		if (templateCsv == null) {
			throw new IllegalArgumentException("Template CSV di risposta non fornito per il tracciato " + tracciato.getId());
		}
		StringBuilder sb = new StringBuilder();
		String header = templateCsv.headerRisposta();
		sb.append(header);
		if (!header.endsWith("\n")) {
			sb.append('\n');
		}
		forEachOperazione(tracciato.getId(), op -> {
			Map<String, Object> model = new HashMap<>();
			model.put("headerRisposta", header);
			if (tracciato.getCodDominio() != null) {
				model.put("idDominio", tracciato.getCodDominio());
			}
			if (tracciato.getCodTipoVersamento() != null) {
				model.put("idTipoVersamento", tracciato.getCodTipoVersamento());
			}
			model.put("esitoOperazione", op.getStato() != null ? op.getStato().name() : "");
			model.put("descrizioneEsitoOperazione", op.getDettaglioEsito() != null ? op.getDettaglioEsito() : "");
			model.put("tipoOperazione", op.getTipoOperazione() != null ? op.getTipoOperazione().name() : "");
			String riga = this.trasformazioneCsvService.applica("csvRisposta", templateCsv.templateRispostaBase64(), model);
			sb.append(riga);
			if (!riga.endsWith("\n")) {
				sb.append('\n');
			}
		});
		return sb.toString().getBytes(StandardCharsets.UTF_8);
	}

	private EsitoOperazionePendenza toEsitoOperazione(Operazione op) {
		return EsitoOperazionePendenza.builder()
				.numero(op.getLineaElaborazione())
				.tipoOperazione(op.getTipoOperazione() != null ? op.getTipoOperazione().name() : null)
				.stato(op.getStato() != null ? op.getStato().name() : null)
				.descrizioneEsito(op.getDettaglioEsito())
				.codVersamentoEnte(op.getCodVersamentoEnte())
				.iuv(op.getIuv())
				.build();
	}

	private void forEachOperazione(Long idTracciato, Consumer<Operazione> consumer) {
		int pagina = 0;
		List<Operazione> operazioni;
		do {
			operazioni = this.operazioneRepository.findByIdTracciatoOrderByLineaElaborazioneAsc(idTracciato,
					PageRequest.of(pagina, PAGE_SIZE));
			operazioni.forEach(consumer);
			pagina++;
		} while (operazioni.size() == PAGE_SIZE);
	}
}

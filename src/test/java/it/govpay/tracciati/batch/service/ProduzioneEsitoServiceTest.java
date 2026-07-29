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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import it.govpay.tracciati.batch.dto.EsitoCsvTemplate;
import it.govpay.tracciati.batch.dto.EsitoTracciatoPendenze;
import it.govpay.tracciati.batch.entity.FormatoTracciato;
import it.govpay.tracciati.batch.entity.Operazione;
import it.govpay.tracciati.batch.entity.StatoOperazione;
import it.govpay.tracciati.batch.entity.TipoOperazione;
import it.govpay.tracciati.batch.entity.Tracciato;
import it.govpay.tracciati.batch.repository.OperazioneRepository;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ProduzioneEsitoServiceTest {

	private final OperazioneRepository operazioneRepository = mock(OperazioneRepository.class);
	private final ObjectMapper objectMapper = JsonMapper.builder()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build();
	private final ProduzioneEsitoService service = new ProduzioneEsitoService(
			this.operazioneRepository, this.objectMapper, new TrasformazioneCsvService());

	private Operazione op(long linea, TipoOperazione tipo, StatoOperazione stato, String cod) {
		return Operazione.builder()
				.lineaElaborazione(linea).tipoOperazione(tipo).stato(stato)
				.codVersamentoEnte(cod).dettaglioEsito("dett-" + linea).iuv("IUV" + linea).build();
	}

	@Test
	void esitoJsonSeparaInserimentiEAnnullamenti() {
		Tracciato tracciato = Tracciato.builder().id(1L).formato(FormatoTracciato.JSON)
				.fileNameRichiesta("tracciato.json").build();
		when(this.operazioneRepository.findByIdTracciatoOrderByLineaElaborazioneAsc(eq(1L), any(Pageable.class)))
				.thenReturn(List.of(
						op(1, TipoOperazione.ADD, StatoOperazione.ESEGUITO_OK, "P1"),
						op(2, TipoOperazione.ADD, StatoOperazione.ESEGUITO_KO, "P2"),
						op(3, TipoOperazione.DEL, StatoOperazione.ESEGUITO_OK, "P1")));

		this.service.produciEsito(tracciato, null);

		assertEquals("esito_tracciato.json", tracciato.getFileNameEsito());
		EsitoTracciatoPendenze esito = this.objectMapper.readValue(tracciato.getRawEsito(), EsitoTracciatoPendenze.class);
		assertEquals("tracciato.json", esito.getIdTracciato());
		assertEquals(2, esito.getInserimenti().size());
		assertEquals(1, esito.getAnnullamenti().size());
		assertEquals("P1", esito.getInserimenti().get(0).getCodVersamentoEnte());
		assertEquals("ESEGUITO_KO", esito.getInserimenti().get(1).getStato());
	}

	@Test
	void esitoCsvApplicaTemplateRispostaPerOperazione() {
		Tracciato tracciato = Tracciato.builder().id(1L).formato(FormatoTracciato.CSV)
				.codDominio("DOM01").codTipoVersamento("TARI").fileNameRichiesta("tracciato.csv").build();
		when(this.operazioneRepository.findByIdTracciatoOrderByLineaElaborazioneAsc(eq(1L), any(Pageable.class)))
				.thenReturn(List.of(op(1, TipoOperazione.ADD, StatoOperazione.ESEGUITO_OK, "P1")));

		String template = "${tipoOperazione};${esitoOperazione};${descrizioneEsitoOperazione}";
		String templateBase64 = Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8));
		EsitoCsvTemplate templateCsv = new EsitoCsvTemplate("TIPO;ESITO;DETTAGLIO", templateBase64);

		this.service.produciEsito(tracciato, templateCsv);

		String esito = new String(tracciato.getRawEsito(), StandardCharsets.UTF_8);
		assertEquals("esito_tracciato.csv", tracciato.getFileNameEsito());
		assertTrue(esito.startsWith("TIPO;ESITO;DETTAGLIO\n"), "deve iniziare con l'intestazione");
		assertTrue(esito.contains("ADD;ESEGUITO_OK;dett-1"), "deve contenere la riga trasformata dell'operazione");
	}
}

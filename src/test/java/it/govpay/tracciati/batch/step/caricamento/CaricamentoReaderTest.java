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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import it.govpay.tracciati.batch.dto.RigaTracciato;
import it.govpay.tracciati.batch.dto.TracciatoPendenza;
import it.govpay.tracciati.batch.entity.TipoOperazione;
import it.govpay.tracciati.batch.entity.Tracciato;
import it.govpay.tracciati.batch.service.TrasformazioneCsvService;
import it.govpay.tracciati.batch.util.CsvTracciatoUtils;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class CaricamentoReaderTest {

	private final ObjectMapper objectMapper = JsonMapper.builder()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.build();

	@Test
	void csvUtilsContaESplittaLinee() {
		byte[] raw = "header\nriga1\nriga2\n".getBytes(StandardCharsets.UTF_8);
		assertEquals(3, CsvTracciatoUtils.countLines(raw));
		List<String> senzaHeader = CsvTracciatoUtils.splitCsv(raw, 1);
		assertEquals(2, senzaHeader.size());
		assertEquals("riga1", senzaHeader.get(0));
		assertEquals("riga2", senzaHeader.get(1));
	}

	@Test
	void jsonReaderProduceAddEDelSaltandoICheckpoint() {
		String json = "{\"inserimenti\":[{\"idA2A\":\"A\",\"idPendenza\":\"P1\",\"importo\":10.0},"
				+ "{\"idA2A\":\"A\",\"idPendenza\":\"P2\",\"importo\":20.0}],"
				+ "\"annullamenti\":[{\"idA2A\":\"A\",\"idPendenza\":\"P1\",\"motivoAnnullamento\":\"x\"}]}";
		Tracciato tracciato = Tracciato.builder()
				.codDominio("DOM01")
				.rawRichiesta(json.getBytes(StandardCharsets.UTF_8))
				.build();
		TracciatoPendenza beanDati = new TracciatoPendenza();

		JsonTracciatoItemReader reader = new JsonTracciatoItemReader(this.objectMapper);
		reader.bind(tracciato, beanDati);

		RigaTracciato r1 = reader.read();
		RigaTracciato r2 = reader.read();
		RigaTracciato r3 = reader.read();
		assertNull(reader.read());

		assertEquals(TipoOperazione.ADD, r1.getTipoOperazione());
		assertEquals(1L, r1.getLinea());
		assertEquals("P1", r1.getPendenza().getIdPendenza());
		assertEquals("DOM01", r1.getPendenza().getIdDominio());
		assertEquals(TipoOperazione.ADD, r2.getTipoOperazione());
		assertEquals(2L, r2.getLinea());
		assertEquals(TipoOperazione.DEL, r3.getTipoOperazione());
		assertEquals(3L, r3.getLinea());
		assertEquals("P1", r3.getAnnullamento().getIdPendenza());
	}

	@Test
	void jsonReaderRiprendeDaCheckpoint() {
		String json = "{\"inserimenti\":[{\"idPendenza\":\"P1\",\"importo\":10.0},{\"idPendenza\":\"P2\",\"importo\":20.0}]}";
		Tracciato tracciato = Tracciato.builder().codDominio("DOM01")
				.rawRichiesta(json.getBytes(StandardCharsets.UTF_8)).build();
		TracciatoPendenza beanDati = new TracciatoPendenza();
		beanDati.setLineaElaborazioneAdd(1); // già elaborata la prima

		JsonTracciatoItemReader reader = new JsonTracciatoItemReader(this.objectMapper);
		reader.bind(tracciato, beanDati);

		RigaTracciato r = reader.read();
		assertNull(reader.read());
		assertEquals(2L, r.getLinea());
		assertEquals("P2", r.getPendenza().getIdPendenza());
	}

	@Test
	void csvReaderTrasformaRigaConTemplateFreemarker() {
		String template = "{\"idPendenza\":\"${lineaCsvRichiesta}\",\"importo\":1.00}";
		String templateBase64 = Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8));
		byte[] csv = "intestazione\nABC123\n".getBytes(StandardCharsets.UTF_8);

		Tracciato tracciato = Tracciato.builder().codDominio("DOM01").codTipoVersamento("TARI")
				.rawRichiesta(csv).build();
		TracciatoPendenza beanDati = new TracciatoPendenza();
		beanDati.setLineaElaborazioneAdd(1); // salta intestazione

		CsvTracciatoItemReader reader = new CsvTracciatoItemReader(this.objectMapper, new TrasformazioneCsvService());
		reader.bind(tracciato, beanDati, templateBase64);

		RigaTracciato r = reader.read();
		assertNull(reader.read());
		assertNotNull(r);
		assertEquals(TipoOperazione.ADD, r.getTipoOperazione());
		assertEquals(1L, r.getLinea());
		assertEquals("ABC123", r.getPendenza().getIdPendenza());
		assertEquals("DOM01", r.getPendenza().getIdDominio());
	}

	@Test
	void processorMarcaRigaNonValida() {
		CaricamentoItemProcessor processor = new CaricamentoItemProcessor();

		RigaTracciato valida = new RigaTracciato();
		valida.setTipoOperazione(TipoOperazione.ADD);
		it.govpay.tracciati.batch.dto.PendenzaPost p = new it.govpay.tracciati.batch.dto.PendenzaPost();
		p.setIdPendenza("P1");
		p.setImporto(new java.math.BigDecimal("10.00"));
		valida.setPendenza(p);
		assertTrue(processor.process(valida).isValida());

		RigaTracciato invalida = new RigaTracciato();
		invalida.setTipoOperazione(TipoOperazione.ADD);
		invalida.setPendenza(new it.govpay.tracciati.batch.dto.PendenzaPost());
		RigaTracciato esito = processor.process(invalida);
		assertNotNull(esito.getErrore());
	}
}

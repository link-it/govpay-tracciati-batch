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

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

/**
 * Trasforma una riga di tracciato CSV nel corrispondente JSON di pendenza applicando il
 * template FreeMarker configurato per il tipo versamento/dominio (colonna
 * {@code trac_csv_template_richiesta}, memorizzata in Base64).
 *
 * <p>Le variabili esposte al template ricalcano quelle della procedura legacy
 * ({@code TrasformazioniUtils.fillDynamicMapRichiestaTracciatoCSV}): {@code lineaCsvRichiesta},
 * {@code idDominio}, {@code idTipoVersamento}, {@code date}, {@code context}, {@code responseMap}.</p>
 */
@Service
public class TrasformazioneCsvService {

	private final Configuration configuration;

	public TrasformazioneCsvService() {
		this.configuration = new Configuration(Configuration.VERSION_2_3_34);
		this.configuration.setDefaultEncoding(StandardCharsets.UTF_8.name());
		this.configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
		this.configuration.setLogTemplateExceptions(false);
		this.configuration.setNumberFormat("computer");
		this.configuration.setWrapUncheckedExceptions(true);
	}

	/**
	 * Applica il template di richiesta alla riga CSV.
	 *
	 * @param templateRichiestaBase64 contenuto della colonna {@code trac_csv_template_richiesta} (Base64, eventualmente tra virgolette)
	 * @param lineaCsv                riga CSV da trasformare
	 * @param codDominio              codice dominio
	 * @param codTipoVersamento       codice tipo versamento (può essere null)
	 * @return JSON della pendenza risultante dalla trasformazione
	 */
	public String trasformaRigaCsv(String templateRichiestaBase64, String lineaCsv, String codDominio, String codTipoVersamento) {
		Map<String, Object> model = new HashMap<>();
		model.put("lineaCsvRichiesta", lineaCsv);
		if (codDominio != null) {
			model.put("idDominio", codDominio);
		}
		if (codTipoVersamento != null) {
			model.put("idTipoVersamento", codTipoVersamento);
		}
		return applica("csvRichiesta", templateRichiestaBase64, model);
	}

	/**
	 * Applica un template FreeMarker (Base64) al modello dato. Le variabili comuni
	 * ({@code date}, {@code context}, {@code responseMap}) sono aggiunte se assenti.
	 *
	 * @param nomeTemplate      nome logico del template (per i messaggi di errore)
	 * @param templateBase64    contenuto del template (Base64, eventualmente tra virgolette)
	 * @param model             variabili esposte al template
	 * @return risultato dell'elaborazione del template
	 */
	public String applica(String nomeTemplate, String templateBase64, Map<String, Object> model) {
		byte[] template = decodeTemplate(templateBase64);
		model.putIfAbsent("date", new Date());
		model.putIfAbsent("context", new HashMap<String, Object>());
		model.putIfAbsent("responseMap", new HashMap<String, Object>());
		try {
			Template freemarkerTemplate = new Template(nomeTemplate,
					new StringReader(new String(template, StandardCharsets.UTF_8)), this.configuration);
			StringWriter writer = new StringWriter();
			freemarkerTemplate.process(model, writer);
			return writer.toString();
		} catch (Exception e) {
			throw new IllegalStateException("Errore nell'applicazione del template FreeMarker [" + nomeTemplate + "]: " + e.getMessage(), e);
		}
	}

	private byte[] decodeTemplate(String templateBase64) {
		String value = templateBase64;
		if (value.startsWith("\"")) {
			value = value.substring(1);
		}
		if (value.endsWith("\"")) {
			value = value.substring(0, value.length() - 1);
		}
		return Base64.getDecoder().decode(value.getBytes(StandardCharsets.UTF_8));
	}
}

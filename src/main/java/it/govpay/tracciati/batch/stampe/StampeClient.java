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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import it.govpay.tracciati.stampe.client.model.CdsViolation;
import it.govpay.tracciati.stampe.client.model.PaymentNotice;

/**
 * Client HTTP verso il microservizio stampe {@code govpay-stampe-api} (contratto OpenAPI v1.1.0).
 *
 * <p>Usa Spring {@link RestClient} (Jackson 3) per serializzare la richiesta e ricevere il PDF
 * binario. Il base URL è configurabile via {@code govpay.tracciati.stampe.base-url}.</p>
 *
 * <p>TODO(8): integrare autenticazione/timeout/retry (eventualmente tramite il connettore
 * configurato in {@code govpay-common} {@code ConnettoreService}).</p>
 */
@Component
public class StampeClient {

	private final RestClient restClient;

	public StampeClient(@Value("${govpay.tracciati.stampe.base-url:}") String baseUrl) {
		this.restClient = RestClient.builder().baseUrl(baseUrl).build();
	}

	/** Avviso di pagamento standard: {@code POST /standard} → PDF. */
	public byte[] creaAvvisoStandard(PaymentNotice paymentNotice) {
		return this.restClient.post()
				.uri("/standard")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_PDF)
				.body(paymentNotice)
				.retrieve()
				.body(byte[].class);
	}

	/** Avviso per violazione Codice della Strada: {@code POST /cds_violation} → PDF. */
	public byte[] creaAvvisoViolazioneCds(CdsViolation cdsViolation) {
		return this.restClient.post()
				.uri("/cds_violation")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_PDF)
				.body(cdsViolation)
				.retrieve()
				.body(byte[].class);
	}
}

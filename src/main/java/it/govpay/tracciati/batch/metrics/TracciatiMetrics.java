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
package it.govpay.tracciati.batch.metrics;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Metriche di business del batch tracciati, esposte via Micrometer/Prometheus (Actuator).
 */
@Component
public class TracciatiMetrics {

	private final Counter tracciatiCompletati;
	private final Counter tracciatiScartati;

	public TracciatiMetrics(MeterRegistry registry) {
		this.tracciatiCompletati = Counter.builder("govpay.tracciati.completati")
				.description("Numero di tracciati elaborati con successo").register(registry);
		this.tracciatiScartati = Counter.builder("govpay.tracciati.scartati")
				.description("Numero di tracciati scartati per errore").register(registry);
	}

	public void tracciatoCompletato() {
		this.tracciatiCompletati.increment();
	}

	public void tracciatoScartato() {
		this.tracciatiScartati.increment();
	}
}

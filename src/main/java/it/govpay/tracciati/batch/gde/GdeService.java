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
package it.govpay.tracciati.batch.gde;

import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import it.govpay.common.client.gde.HttpDataHolder;
import it.govpay.common.configurazione.model.GdeInterfaccia;
import it.govpay.common.configurazione.model.Giornale;
import it.govpay.common.configurazione.service.ConfigurazioneService;
import it.govpay.common.gde.AbstractGdeService;
import it.govpay.common.gde.GdeEventInfo;
import it.govpay.tracciati.batch.entity.Tracciato;
import it.govpay.gde.client.beans.CategoriaEvento;
import it.govpay.gde.client.beans.ComponenteEvento;
import it.govpay.gde.client.beans.EsitoEvento;
import it.govpay.gde.client.beans.NuovoEvento;
import tools.jackson.databind.ObjectMapper;

/**
 * Invia al Giornale degli Eventi (GDE) un evento <b>interno</b> al termine dell'elaborazione di un
 * tracciato (esito OK o terminata con errore). Estende {@link AbstractGdeService} di govpay-common.
 *
 * <p>Invio asincrono e best-effort: un errore nell'invio al GDE non interrompe il batch.</p>
 */
@Service
public class GdeService extends AbstractGdeService {

	private static final Logger log = LoggerFactory.getLogger(GdeService.class);
	private static final String TIPO_EVENTO = "elaborazioneTracciatoPendenze";

	private final ConfigurazioneService configurazioneService;
	private final String clusterId;

	public GdeService(ObjectMapper objectMapper,
			@Qualifier("asyncHttpExecutor") Executor asyncHttpExecutor,
			ConfigurazioneService configurazioneService,
			@Value("${govpay.batch.cluster-id:GovPay-Tracciati-Batch}") String clusterId) {
		super(objectMapper, asyncHttpExecutor, configurazioneService);
		this.configurazioneService = configurazioneService;
		this.clusterId = clusterId;
	}

	@Override
	protected String getGdeEndpoint() {
		return this.configurazioneService.getServizioGDE().getUrl() + "/eventi";
	}

	@Override
	protected NuovoEvento convertToGdeEvent(GdeEventInfo eventInfo) {
		throw new UnsupportedOperationException("GdeService usa l'invio diretto di NuovoEvento");
	}

	@Override
	protected GdeInterfaccia getConfigurazioneComponente(ComponenteEvento componente, Giornale giornale) {
		// evento interno: nessuna interfaccia specifica
		return null;
	}

	/** Registra l'esito dell'elaborazione del tracciato (OK o errore). */
	public void inviaEsitoElaborazione(Tracciato tracciato, boolean ok) {
		if (!isAbilitato()) {
			return;
		}
		NuovoEvento evento = new NuovoEvento();
		evento.setComponente(ComponenteEvento.GOVPAY);
		evento.setCategoriaEvento(CategoriaEvento.INTERNO);
		evento.setTipoEvento(TIPO_EVENTO);
		evento.setDataEvento(OffsetDateTime.now());
		evento.setEsito(ok ? EsitoEvento.OK : EsitoEvento.KO);
		evento.setClusterId(this.clusterId);
		sendEventAsync(evento);
	}

	private void sendEventAsync(NuovoEvento evento) {
		CompletableFuture.runAsync(() -> {
			try {
				getGdeRestTemplate().postForEntity(getGdeEndpoint(), evento, Void.class);
			} catch (Exception e) {
				log.warn("Invio evento GDE [{}] non riuscito (il batch continua): {}", evento.getTipoEvento(), e.getMessage());
			} finally {
				HttpDataHolder.clear();
			}
		}, this.asyncExecutor);
	}
}

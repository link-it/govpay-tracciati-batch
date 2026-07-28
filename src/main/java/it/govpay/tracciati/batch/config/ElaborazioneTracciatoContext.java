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
package it.govpay.tracciati.batch.config;

import java.util.List;

import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import it.govpay.tracciati.batch.dto.TracciatoPendenza;
import it.govpay.tracciati.batch.entity.StatoElaborazione;
import it.govpay.tracciati.batch.entity.TipoTracciato;
import it.govpay.tracciati.batch.entity.Tracciato;
import it.govpay.tracciati.batch.repository.TracciatoRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Contesto dell'esecuzione del job, con scope per singola esecuzione ({@code @JobScope}):
 * individua il tracciato da elaborare e ne espone lo stato {@code bean_dati}.
 *
 * <p>Il tracciato è quello indicato dal job parameter {@code idTracciato} (avvio on-demand mirato);
 * se assente, viene selezionato il <b>prossimo tracciato pendente</b> (stato ELABORAZIONE/IN_STAMPA),
 * così il job resta lanciabile dallo scheduler/controller comuni ed elabora un tracciato per volta
 * (D5).</p>
 */
@Component
@JobScope
public class ElaborazioneTracciatoContext {

	private final TracciatoRepository tracciatoRepository;
	private final ObjectMapper objectMapper;
	private final Long idTracciato;

	private boolean caricato = false;
	private Tracciato tracciato;
	private TracciatoPendenza beanDati;

	public ElaborazioneTracciatoContext(TracciatoRepository tracciatoRepository, ObjectMapper objectMapper,
			@Value("#{jobParameters['idTracciato']}") Long idTracciato) {
		this.tracciatoRepository = tracciatoRepository;
		this.objectMapper = objectMapper;
		this.idTracciato = idTracciato;
	}

	private void carica() {
		if (this.caricato) {
			return;
		}
		this.caricato = true;
		if (this.idTracciato != null) {
			this.tracciato = this.tracciatoRepository.findById(this.idTracciato).orElse(null);
		} else {
			List<Tracciato> pendenti = this.tracciatoRepository.findByTipoAndStatoInOrderByIdAsc(
					TipoTracciato.PENDENZA,
					List.of(StatoElaborazione.ELABORAZIONE, StatoElaborazione.IN_STAMPA),
					PageRequest.of(0, 1));
			this.tracciato = pendenti.isEmpty() ? null : pendenti.get(0);
		}
		if (this.tracciato != null) {
			String bd = this.tracciato.getBeanDati();
			this.beanDati = (bd != null && !bd.isBlank())
					? this.objectMapper.readValue(bd, TracciatoPendenza.class)
					: new TracciatoPendenza();
		}
	}

	/** True se c'è un tracciato da elaborare in questa esecuzione. */
	public boolean isPresente() {
		carica();
		return this.tracciato != null;
	}

	public Tracciato getTracciato() {
		carica();
		return this.tracciato;
	}

	public TracciatoPendenza getBeanDati() {
		carica();
		return this.beanDati;
	}
}

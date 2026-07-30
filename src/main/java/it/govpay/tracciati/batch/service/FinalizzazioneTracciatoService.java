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

import java.time.LocalDateTime;
import java.util.Date;

import org.springframework.stereotype.Service;

import it.govpay.tracciati.batch.dto.StatoTracciatoType;
import it.govpay.tracciati.batch.dto.TracciatoPendenza;
import it.govpay.tracciati.batch.entity.StatoElaborazione;
import it.govpay.tracciati.batch.entity.Tracciato;
import it.govpay.tracciati.batch.gde.GdeService;
import it.govpay.tracciati.batch.metrics.TracciatiMetrics;
import it.govpay.tracciati.batch.repository.TracciatoRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Gestisce le transizioni finali del tracciato e la persistenza dello stato/{@code bean_dati},
 * replicando la macchina a stati della procedura legacy:
 * <ul>
 *   <li>fine caricamento → stepElaborazione CARICAMENTO_OK/KO; poi IN_STAMPA (se stampaAvvisi)
 *       o COMPLETATO;</li>
 *   <li>fine stampa → COMPLETATO;</li>
 *   <li>errore → SCARTATO (stepElaborazione ANNULLATO).</li>
 * </ul>
 * Aggiorna sempre {@code bean_dati} e {@code stato} (compatibilità console GovPay, D6).
 */
@Service
public class FinalizzazioneTracciatoService {

	private static final int MAX_DESCRIZIONE = 256;

	private final TracciatoRepository tracciatoRepository;
	private final ObjectMapper objectMapper;
	private final TracciatiMetrics metrics;
	private final GdeService gdeService;

	public FinalizzazioneTracciatoService(TracciatoRepository tracciatoRepository, ObjectMapper objectMapper,
			TracciatiMetrics metrics, GdeService gdeService) {
		this.tracciatoRepository = tracciatoRepository;
		this.objectMapper = objectMapper;
		this.metrics = metrics;
		this.gdeService = gdeService;
	}

	/** Chiude la fase di caricamento: calcola l'esito di dettaglio e decide IN_STAMPA vs COMPLETATO. */
	public void completaCaricamento(Tracciato tracciato, TracciatoPendenza beanDati) {
		impostaStatoDettaglio(beanDati);
		if (beanDati.isStampaAvvisi()) {
			beanDati.setNumStampeTotali(beanDati.getNumAddOk());
			beanDati.setNumStampeOk(0);
			beanDati.setNumStampeKo(0);
			beanDati.setDataUltimoAggiornamento(new Date());
			tracciato.setStato(StatoElaborazione.IN_STAMPA);
			salva(tracciato, beanDati);
		} else {
			completa(tracciato, beanDati);
		}
	}

	/** Chiude la fase di stampa: il tracciato è COMPLETATO. */
	public void completaStampa(Tracciato tracciato, TracciatoPendenza beanDati) {
		completa(tracciato, beanDati);
	}

	/** Scarta il tracciato per errore non gestito. */
	public void scarta(Tracciato tracciato, TracciatoPendenza beanDati, String errore) {
		String descrizione = "Errore durante l'elaborazione del tracciato: " + errore;
		tracciato.setDescrizioneStato(tronca(descrizione));
		tracciato.setStato(StatoElaborazione.SCARTATO);
		tracciato.setDataCompletamento(LocalDateTime.now());
		if (beanDati != null) {
			beanDati.setStepElaborazione(StatoTracciatoType.ANNULLATO.toString());
			beanDati.setDescrizioneStepElaborazione(tronca(descrizione));
			beanDati.setDataUltimoAggiornamento(new Date());
		}
		salva(tracciato, beanDati);
		this.metrics.tracciatoScartato();
		this.gdeService.inviaEsitoElaborazione(tracciato, false);
	}

	private void completa(Tracciato tracciato, TracciatoPendenza beanDati) {
		beanDati.setDataUltimoAggiornamento(new Date());
		tracciato.setStato(StatoElaborazione.COMPLETATO);
		tracciato.setDataCompletamento(LocalDateTime.now());
		salva(tracciato, beanDati);
		this.metrics.tracciatoCompletato();
		this.gdeService.inviaEsitoElaborazione(tracciato, true);
	}

	private void impostaStatoDettaglio(TracciatoPendenza beanDati) {
		boolean conErrori = (beanDati.getNumAddKo() + beanDati.getNumDelKo()) > 0;
		beanDati.setStepElaborazione((conErrori ? StatoTracciatoType.CARICAMENTO_KO : StatoTracciatoType.CARICAMENTO_OK).toString());
	}

	private void salva(Tracciato tracciato, TracciatoPendenza beanDati) {
		if (beanDati != null) {
			tracciato.setBeanDati(this.objectMapper.writeValueAsString(beanDati));
		}
		this.tracciatoRepository.save(tracciato);
	}

	private String tronca(String valore) {
		return valore.length() > MAX_DESCRIZIONE ? valore.substring(0, MAX_DESCRIZIONE - 1) : valore;
	}
}

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

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

import it.govpay.tracciati.batch.dto.EsitoCaricamento;
import it.govpay.tracciati.batch.dto.RigaTracciato;
import it.govpay.tracciati.batch.dto.TracciatoPendenza;
import it.govpay.tracciati.batch.entity.Operazione;
import it.govpay.tracciati.batch.entity.StatoOperazione;
import it.govpay.tracciati.batch.entity.TipoOperazione;
import it.govpay.tracciati.batch.entity.Tracciato;
import it.govpay.tracciati.batch.repository.OperazioneRepository;
import it.govpay.tracciati.batch.service.CaricamentoService;

/**
 * Writer dello step di caricamento: per ogni riga esegue il caricamento/annullamento della
 * pendenza (delegato a {@link CaricamentoService}), registra l'{@link Operazione} corrispondente
 * (upsert idempotente per {@code (idTracciato, linea)}) e aggiorna i contatori del {@code bean_dati}.
 *
 * <p>La persistenza del {@code bean_dati}/stato del tracciato sul DB avviene a livello di step
 * (commit del chunk) e sarà cablata in fase di assemblaggio dello step; qui i contatori sono
 * aggiornati in memoria sull'oggetto {@link TracciatoPendenza} associato.</p>
 */
public class CaricamentoItemWriter implements ItemWriter<RigaTracciato> {

	private final CaricamentoService caricamentoService;
	private final OperazioneRepository operazioneRepository;

	private Tracciato tracciato;
	private TracciatoPendenza beanDati;

	public CaricamentoItemWriter(CaricamentoService caricamentoService, OperazioneRepository operazioneRepository) {
		this.caricamentoService = caricamentoService;
		this.operazioneRepository = operazioneRepository;
	}

	/** Associa il tracciato corrente e il bean di stato su cui accumulare i contatori. */
	public void bind(Tracciato tracciato, TracciatoPendenza beanDati) {
		this.tracciato = tracciato;
		this.beanDati = beanDati;
	}

	@Override
	public void write(Chunk<? extends RigaTracciato> chunk) {
		for (RigaTracciato riga : chunk) {
			EsitoCaricamento esito = elabora(riga);
			salvaOperazione(riga, esito);
			aggiornaContatori(riga, esito);
		}
		this.beanDati.setDataUltimoAggiornamento(new Date());
	}

	private EsitoCaricamento elabora(RigaTracciato riga) {
		if (!riga.isValida()) {
			return EsitoCaricamento.ko(riga.getErrore());
		}
		if (riga.getTipoOperazione() == TipoOperazione.ADD) {
			return this.caricamentoService.caricaPendenza(riga.getPendenza(), this.tracciato);
		}
		return this.caricamentoService.annullaPendenza(riga.getAnnullamento(), this.tracciato);
	}

	private void salvaOperazione(RigaTracciato riga, EsitoCaricamento esito) {
		Operazione operazione = this.operazioneRepository
				.findByIdTracciatoAndLineaElaborazione(this.tracciato.getId(), riga.getLinea())
				.orElseGet(Operazione::new);

		operazione.setIdTracciato(this.tracciato.getId());
		operazione.setLineaElaborazione(riga.getLinea());
		operazione.setTipoOperazione(riga.getTipoOperazione());
		operazione.setStato(esito.getStato());
		operazione.setCodDominio(this.tracciato.getCodDominio());
		if (riga.getJsonRichiesta() != null) {
			operazione.setDatiRichiesta(riga.getJsonRichiesta().getBytes(StandardCharsets.UTF_8));
		}
		operazione.setDettaglioEsito(tronca(esito.getDettaglioEsito()));
		operazione.setCodVersamentoEnte(risolviCodVersamentoEnte(riga, esito));
		operazione.setIdApplicazione(esito.getIdApplicazione());
		operazione.setIdVersamento(esito.getIdVersamento());
		operazione.setIdStampa(esito.getIdStampa());
		operazione.setIuv(esito.getIuv());

		this.operazioneRepository.save(operazione);
	}

	private void aggiornaContatori(RigaTracciato riga, EsitoCaricamento esito) {
		boolean ok = esito.getStato() == StatoOperazione.ESEGUITO_OK;
		if (riga.getTipoOperazione() == TipoOperazione.ADD) {
			if (ok) {
				this.beanDati.setNumAddOk(this.beanDati.getNumAddOk() + 1);
			} else {
				this.beanDati.setNumAddKo(this.beanDati.getNumAddKo() + 1);
				this.beanDati.setDescrizioneStepElaborazione(esito.getDettaglioEsito());
			}
			this.beanDati.setLineaElaborazioneAdd(this.beanDati.getLineaElaborazioneAdd() + 1);
		} else {
			if (ok) {
				this.beanDati.setNumDelOk(this.beanDati.getNumDelOk() + 1);
			} else {
				this.beanDati.setNumDelKo(this.beanDati.getNumDelKo() + 1);
				this.beanDati.setDescrizioneStepElaborazione(esito.getDettaglioEsito());
			}
			this.beanDati.setLineaElaborazioneDel(this.beanDati.getLineaElaborazioneDel() + 1);
		}
	}

	private String risolviCodVersamentoEnte(RigaTracciato riga, EsitoCaricamento esito) {
		if (esito.getCodVersamentoEnte() != null) {
			return esito.getCodVersamentoEnte();
		}
		if (riga.getPendenza() != null) {
			return riga.getPendenza().getIdPendenza();
		}
		if (riga.getAnnullamento() != null) {
			return riga.getAnnullamento().getIdPendenza();
		}
		return null;
	}

	private String tronca(String dettaglio) {
		if (dettaglio == null) {
			return null;
		}
		return dettaglio.length() > 255 ? dettaglio.substring(0, 255) : dettaglio;
	}
}

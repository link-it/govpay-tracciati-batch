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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import it.govpay.tracciati.batch.dto.RisultatoStampa;
import it.govpay.tracciati.batch.entity.Stampa;
import it.govpay.tracciati.batch.entity.Versamento;
import it.govpay.tracciati.batch.repository.StampaRepository;
import it.govpay.tracciati.stampe.client.model.Iban;

/**
 * Produce l'avviso PDF di un'unità di stampa ({@link AvvisoDaStampare}) tramite il microservizio e
 * ne persiste il risultato nella tabella {@code stampe}. La tipologia di avviso non è scelta qui:
 * dipende dai dati (rate, soglie, IBAN postale) e l'unica decisione è tra l'endpoint standard e
 * quello delle violazioni al Codice della Strada.
 *
 * <p>In caso di errore la stampa viene marcata KO e l'elaborazione del tracciato prosegue, come
 * nella procedura legacy.</p>
 */
@Service
public class StampaAvvisoService {

	private static final Logger log = LoggerFactory.getLogger(StampaAvvisoService.class);
	private static final String TIPO_STAMPA_AVVISO = "AVVISO";

	private final AvvisoMapper avvisoMapper;
	private final StampeClient stampeClient;
	private final StampaRepository stampaRepository;
	private final DatiCreditoreResolver datiCreditoreResolver;
	private final IbanAvvisoResolver ibanAvvisoResolver;

	public StampaAvvisoService(AvvisoMapper avvisoMapper, StampeClient stampeClient,
			StampaRepository stampaRepository, DatiCreditoreResolver datiCreditoreResolver,
			IbanAvvisoResolver ibanAvvisoResolver) {
		this.avvisoMapper = avvisoMapper;
		this.stampeClient = stampeClient;
		this.stampaRepository = stampaRepository;
		this.datiCreditoreResolver = datiCreditoreResolver;
		this.ibanAvvisoResolver = ibanAvvisoResolver;
	}

	public RisultatoStampa stampa(AvvisoDaStampare avviso) {
		List<Versamento> stampabili = avviso.versamenti().stream()
				.filter(versamento -> versamento.getNumeroAvviso() != null)
				.toList();
		if (stampabili.isEmpty()) {
			return RisultatoStampa.ko("Nessuna posizione con numero avviso: stampa non eseguita");
		}
		if (stampabili.size() < avviso.versamenti().size()) {
			log.warn("Avviso {}: {} posizioni su {} senza numero avviso, escluse dalla stampa",
					descrizione(avviso), avviso.versamenti().size() - stampabili.size(), avviso.versamenti().size());
		}

		try {
			Map<Long, Iban> ibanPostali = ibanPostali(stampabili);
			ConfigurazioneAvviso configurazione = ConfigurazioneAvviso.da(stampabili, ibanPostali.keySet());
			if (configurazione.isVuota()) {
				return RisultatoStampa.ko("Nessun importo da riportare sull'avviso " + descrizione(avviso));
			}

			Versamento principale = stampabili.get(0);
			DatiCreditore creditore = this.datiCreditoreResolver.risolvi(principale);
			AvvisoDaStampare daStampare = new AvvisoDaStampare(avviso.documento(), stampabili);

			byte[] pdf;
			if (configurazione.violazioneCds()) {
				pdf = this.stampeClient.creaAvvisoViolazioneCds(
						this.avvisoMapper.toCdsViolation(daStampare, creditore, configurazione, ibanPostali));
			} else {
				pdf = this.stampeClient.creaAvvisoStandard(
						this.avvisoMapper.toPaymentNotice(daStampare, creditore, configurazione, ibanPostali));
			}

			salva(daStampare, pdf);

			return RisultatoStampa.ok(pdf, creditore.creditor().getFiscalCode(), principale.getNumeroAvviso(),
					avviso.numeroDocumento());
		} catch (Exception e) {
			log.error("Errore nella stampa dell'avviso {}: {}", descrizione(avviso), e.getMessage(), e);
			return RisultatoStampa.ko(e.getMessage());
		}
	}

	/** IBAN postale per posizione: presente solo dove il conto della prima voce è di Poste. */
	private Map<Long, Iban> ibanPostali(List<Versamento> versamenti) {
		Map<Long, Iban> ibanPostali = new HashMap<>();
		for (Versamento versamento : versamenti) {
			this.ibanAvvisoResolver.ibanPostale(versamento)
					.ifPresent(iban -> ibanPostali.put(versamento.getId(), iban));
		}
		return ibanPostali;
	}

	/**
	 * Salva il PDF sulla tabella {@code stampe}: una riga per documento se l'avviso raggruppa più
	 * rate, altrimenti una riga per posizione. Se la stampa esiste già viene aggiornata.
	 */
	private void salva(AvvisoDaStampare avviso, byte[] pdf) {
		Long idDocumento = avviso.documento() != null ? avviso.documento().getId() : null;
		Long idVersamento = idDocumento == null ? avviso.versamentoPrincipale().getId() : null;

		Stampa stampa = (idDocumento != null
				? this.stampaRepository.findByIdDocumento(idDocumento)
				: this.stampaRepository.findByIdVersamento(idVersamento))
				.orElseGet(() -> Stampa.builder()
						.tipo(TIPO_STAMPA_AVVISO)
						.idDocumento(idDocumento)
						.idVersamento(idVersamento)
						.build());
		stampa.setPdf(pdf);
		stampa.setDataCreazione(LocalDateTime.now());
		this.stampaRepository.save(stampa);
	}

	private static String descrizione(AvvisoDaStampare avviso) {
		if (avviso.documento() != null) {
			return "del documento " + avviso.documento().getCodDocumento();
		}
		return "della posizione " + avviso.versamentoPrincipale().getCodVersamentoEnte();
	}
}

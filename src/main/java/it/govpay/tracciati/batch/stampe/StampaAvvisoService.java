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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import it.govpay.tracciati.batch.dto.RisultatoStampa;
import it.govpay.tracciati.batch.entity.Versamento;
import it.govpay.tracciati.stampe.client.model.Iban;

/**
 * Produce l'avviso PDF di un'unità di stampa ({@link AvvisoDaStampare}) tramite il microservizio.
 * La tipologia di avviso non è scelta qui: dipende dai dati (rate, soglie, IBAN postale) e l'unica
 * decisione è tra l'endpoint standard e quello delle violazioni al Codice della Strada.
 *
 * <p>Il PDF <b>non viene salvato</b> sulla tabella {@code stampe}: finisce solo nello ZIP del
 * tracciato. È la scelta del vecchio flusso, dove tutti i chiamanti passano {@code salvaSuDB=false}
 * (issue #262, "eliminato il salvataggio duplicato dell'avviso nella tabella stampe"): il PDF è già
 * nello ZIP e la copia per avviso costava un BLOB a vuoto.</p>
 *
 * <p>In caso di errore la stampa viene marcata KO e l'elaborazione del tracciato prosegue, come
 * nella procedura legacy.</p>
 */
@Service
public class StampaAvvisoService {

	private static final Logger log = LoggerFactory.getLogger(StampaAvvisoService.class);

	private final AvvisoMapper avvisoMapper;
	private final StampeClient stampeClient;
	private final DatiCreditoreResolver datiCreditoreResolver;
	private final IbanAvvisoResolver ibanAvvisoResolver;

	public StampaAvvisoService(AvvisoMapper avvisoMapper, StampeClient stampeClient,
			DatiCreditoreResolver datiCreditoreResolver, IbanAvvisoResolver ibanAvvisoResolver) {
		this.avvisoMapper = avvisoMapper;
		this.stampeClient = stampeClient;
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

	private static String descrizione(AvvisoDaStampare avviso) {
		if (avviso.documento() != null) {
			return "del documento " + avviso.documento().getCodDocumento();
		}
		return "della posizione " + avviso.versamentoPrincipale().getCodVersamentoEnte();
	}
}

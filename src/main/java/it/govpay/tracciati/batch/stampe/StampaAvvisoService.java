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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.govpay.tracciati.batch.dto.RisultatoStampa;
import it.govpay.tracciati.batch.entity.Documento;
import it.govpay.tracciati.batch.entity.Stampa;
import it.govpay.tracciati.batch.entity.Versamento;
import it.govpay.tracciati.batch.repository.DocumentoRepository;
import it.govpay.tracciati.batch.repository.StampaRepository;
import it.govpay.tracciati.stampe.client.model.PaymentNotice;

/**
 * Produce l'avviso PDF di una posizione debitoria tramite il microservizio stampe e ne persiste
 * il risultato nella tabella {@code stampe}. Ritorna un {@link RisultatoStampa} con le chiavi per la
 * deduplica nello ZIP. In caso di errore prosegue registrando l'esito KO (come il legacy).
 *
 * <p>Non è un bean di contesto finché il {@link DatiAvvisoResolver} (seam) non ha un'implementazione:
 * viene istanziato in fase di assemblaggio dello step di stampa.</p>
 */
public class StampaAvvisoService {

	private static final Logger log = LoggerFactory.getLogger(StampaAvvisoService.class);
	private static final String TIPO_STAMPA_AVVISO = "AVVISO";

	private final PaymentNoticeMapper paymentNoticeMapper;
	private final StampeClient stampeClient;
	private final StampaRepository stampaRepository;
	private final DocumentoRepository documentoRepository;
	private final DatiAvvisoResolver datiAvvisoResolver;

	public StampaAvvisoService(PaymentNoticeMapper paymentNoticeMapper, StampeClient stampeClient,
			StampaRepository stampaRepository, DocumentoRepository documentoRepository,
			DatiAvvisoResolver datiAvvisoResolver) {
		this.paymentNoticeMapper = paymentNoticeMapper;
		this.stampeClient = stampeClient;
		this.stampaRepository = stampaRepository;
		this.documentoRepository = documentoRepository;
		this.datiAvvisoResolver = datiAvvisoResolver;
	}

	public RisultatoStampa stampa(Versamento versamento) {
		if (versamento.getNumeroAvviso() == null) {
			return RisultatoStampa.ko("Posizione senza numero avviso: stampa non eseguita");
		}
		try {
			DatiAvvisoCreditore dati = this.datiAvvisoResolver.risolvi(versamento);
			PaymentNotice paymentNotice = this.paymentNoticeMapper.toPaymentNotice(versamento, dati);
			byte[] pdf = this.stampeClient.creaAvvisoStandard(paymentNotice);

			Stampa stampa = Stampa.builder()
					.tipo(TIPO_STAMPA_AVVISO)
					.idVersamento(versamento.getId())
					.idDocumento(versamento.getIdDocumento())
					.pdf(pdf)
					.dataCreazione(LocalDateTime.now())
					.build();
			this.stampaRepository.save(stampa);

			return RisultatoStampa.ok(pdf, chiaveDominio(versamento), versamento.getNumeroAvviso(), numeroDocumento(versamento));
		} catch (Exception e) {
			log.error("Errore nella stampa dell'avviso per la posizione {}: {}", versamento.getCodVersamentoEnte(), e.getMessage(), e);
			return RisultatoStampa.ko(e.getMessage());
		}
	}

	private String numeroDocumento(Versamento versamento) {
		if (versamento.getIdDocumento() == null) {
			return null;
		}
		return this.documentoRepository.findById(versamento.getIdDocumento())
				.map(Documento::getCodDocumento)
				.orElse(null);
	}

	private String chiaveDominio(Versamento versamento) {
		return versamento.getIdDominio() != null ? String.valueOf(versamento.getIdDominio()) : "";
	}
}

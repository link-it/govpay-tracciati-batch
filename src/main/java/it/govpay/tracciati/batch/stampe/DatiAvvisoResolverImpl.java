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

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import it.govpay.common.entity.DominioEntity;
import it.govpay.common.repository.DominioRepository;
import it.govpay.tracciati.batch.entity.Versamento;
import it.govpay.tracciati.batch.repository.DominioLogoBatchRepository;
import it.govpay.tracciati.stampe.client.model.Creditor;
import it.govpay.tracciati.stampe.client.model.Languages;

/**
 * Implementazione di {@link DatiAvvisoResolver} che ricava dal dominio (govpay-common) i dati
 * dell'avviso, portando la logica di {@code AvvisoPagamentoUtils.impostaAnagraficaEnteCreditore}:
 * ente creditore = ragione sociale, codice fiscale = codDominio, cbill = dominio.cbill.
 *
 * <p>Logo (punto 5): se il dominio ha un logo si usa quello, altrimenti si usa il logo di default
 * (risorsa configurabile), come nel vecchio GovPay. QR pagoPA composto da {@link QrCodeUtils}.</p>
 *
 * <p>Nota: l'IBAN dell'avviso (opzionale) non è ancora risolto (dipende dalla configurazione
 * accredito del dominio/tributo); le tipologie multi-rata/postale/bilingue/CDS sono affinamenti
 * successivi. Richiede un DB GovPay per la validazione.</p>
 */
@Component
public class DatiAvvisoResolverImpl implements DatiAvvisoResolver {

	private static final Logger log = LoggerFactory.getLogger(DatiAvvisoResolverImpl.class);
	private static final int MAX_RAGIONE_SOCIALE = 50;

	private final DominioRepository dominioRepository;
	private final DominioLogoBatchRepository dominioLogoRepository;
	private final byte[] logoDefault;

	public DatiAvvisoResolverImpl(DominioRepository dominioRepository, DominioLogoBatchRepository dominioLogoRepository,
			ResourceLoader resourceLoader,
			@Value("${govpay.tracciati.stampe.logo-default-path:classpath:stampe/logo-default.png}") String logoDefaultPath) {
		this.dominioRepository = dominioRepository;
		this.dominioLogoRepository = dominioLogoRepository;
		this.logoDefault = caricaLogoDefault(resourceLoader, logoDefaultPath);
	}

	@Override
	public DatiAvvisoCreditore risolvi(Versamento versamento) {
		DominioEntity dominio = this.dominioRepository.findById(versamento.getIdDominio())
				.orElseThrow(() -> new IllegalStateException("Dominio non trovato: id=" + versamento.getIdDominio()));
		String codDominio = dominio.getCodDominio();

		Creditor creditor = new Creditor()
				.fiscalCode(codDominio)
				.businessName(tronca(dominio.getRagioneSociale()))
				.cbillCode(dominio.getCbill());

		byte[] logo = risolviLogo(codDominio);
		String qrcode = QrCodeUtils.buildQrCodePagoPa(versamento.getNumeroAvviso(), codDominio, versamento.getImportoTotale());
		boolean postale = dominio.getAutStampaPoste() != null && !dominio.getAutStampaPoste().isBlank();

		return new DatiAvvisoCreditore(creditor, null, qrcode, logo, Languages.IT, null, postale);
	}

	private byte[] risolviLogo(String codDominio) {
		byte[] logoDominio = this.dominioLogoRepository.findByCodDominio(codDominio)
				.map(it.govpay.common.entity.DominioLogoEntity::getLogo)
				.filter(l -> l != null && l.length > 0)
				.orElse(null);
		return logoDominio != null ? logoDominio : this.logoDefault;
	}

	private String tronca(String ragioneSociale) {
		if (ragioneSociale == null) {
			return null;
		}
		return ragioneSociale.length() > MAX_RAGIONE_SOCIALE ? ragioneSociale.substring(0, MAX_RAGIONE_SOCIALE) : ragioneSociale;
	}

	private byte[] caricaLogoDefault(ResourceLoader resourceLoader, String path) {
		try {
			Resource resource = resourceLoader.getResource(path);
			if (!resource.exists()) {
				log.warn("Logo di default non presente in [{}]: gli avvisi useranno solo il logo del dominio (first_logo obbligatorio)", path);
				return null;
			}
			try (InputStream is = resource.getInputStream()) {
				return is.readAllBytes();
			}
		} catch (IOException e) {
			log.warn("Impossibile caricare il logo di default [{}]: {}", path, e.getMessage());
			return null;
		}
	}
}

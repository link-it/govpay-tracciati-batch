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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import it.govpay.common.entity.DominioEntity;
import it.govpay.common.entity.DominioLogoEntity;
import it.govpay.common.repository.DominioLogoRepository;
import it.govpay.common.repository.DominioRepository;
import it.govpay.tracciati.batch.entity.SingoloVersamento;
import it.govpay.tracciati.batch.entity.UnitaOperativa;
import it.govpay.tracciati.batch.entity.Versamento;
import it.govpay.tracciati.batch.repository.SingoloVersamentoRepository;
import it.govpay.tracciati.batch.repository.UnitaOperativaRepository;
import it.govpay.tracciati.stampe.client.model.Creditor;

/**
 * Port di {@code AvvisoPagamentoUtils.impostaAnagraficaEnteCreditore}: compone l'ente creditore
 * dell'avviso a partire dal dominio e dall'unità operativa della posizione.
 *
 * <ul>
 *   <li>ragione sociale, codice fiscale (codDominio) e cbill dal dominio;</li>
 *   <li>settore ({@code department_name}) e contatti dall'unità operativa della posizione, con
 *       fallback sull'unità operativa {@code EC}, che in GovPay è l'anagrafica del dominio;</li>
 *   <li>logo del dominio, oppure il logo di default configurato;</li>
 *   <li>per le pendenze multibeneficiario, logo secondario del primo dominio diverso.</li>
 * </ul>
 *
 * <p>Il logo va trasmesso come lo conserva GovPay, cioè il <b>testo</b> del data URI
 * ({@code data:image/png;base64,…}): il servizio di stampa lo ridecodifica e lo passa così com'è
 * al template, quindi inviare i byte grezzi dell'immagine produrrebbe un logo illeggibile.</p>
 *
 * <p>Limite del contratto: la vecchia procedura componeva una sola stringa di contatti su tre righe
 * separate da {@code <br/>}, il servizio ne accetta due da 50 caratteri. Si inviano quindi le prime
 * due righe disponibili nell'ordine legacy (sito web, telefono/fax, pec/email).</p>
 */
@Component
public class DatiCreditoreResolverImpl implements DatiCreditoreResolver {

	private static final Logger log = LoggerFactory.getLogger(DatiCreditoreResolverImpl.class);
	private static final int MAX_RAGIONE_SOCIALE = 50;
	private static final int MAX_SETTORE = 50;
	private static final int MAX_INFO_LINE = 50;

	private final DominioRepository dominioRepository;
	private final DominioLogoRepository dominioLogoRepository;
	private final UnitaOperativaRepository unitaOperativaRepository;
	private final SingoloVersamentoRepository singoloVersamentoRepository;
	private final byte[] logoDefault;

	public DatiCreditoreResolverImpl(DominioRepository dominioRepository,
			DominioLogoRepository dominioLogoRepository,
			UnitaOperativaRepository unitaOperativaRepository,
			SingoloVersamentoRepository singoloVersamentoRepository,
			@Value("${govpay.tracciati.stampe.logo.default:}") String logoDefault) {
		this.dominioRepository = dominioRepository;
		this.dominioLogoRepository = dominioLogoRepository;
		this.unitaOperativaRepository = unitaOperativaRepository;
		this.singoloVersamentoRepository = singoloVersamentoRepository;
		this.logoDefault = logoDefault != null && !logoDefault.isBlank()
				? logoDefault.trim().getBytes(StandardCharsets.UTF_8)
				: null;
		if (this.logoDefault == null) {
			log.warn("Logo di default non configurato (govpay.tracciati.stampe.logo.default): le posizioni di domini senza logo non potranno essere stampate");
		}
	}

	@Override
	public DatiCreditore risolvi(Versamento versamento) {
		DominioEntity dominio = this.dominioRepository.findById(versamento.getIdDominio())
				.orElseThrow(() -> new IllegalStateException("Dominio non trovato: id=" + versamento.getIdDominio()));

		Creditor creditor = new Creditor()
				.fiscalCode(dominio.getCodDominio())
				.businessName(tronca(dominio.getRagioneSociale(), MAX_RAGIONE_SOCIALE))
				.cbillCode(dominio.getCbill())
				.postalAuthMessage(dominio.getAutStampaPoste());

		anagrafica(versamento, dominio).ifPresent(uo -> {
			creditor.setDepartmentName(tronca(uo.getArea(), MAX_SETTORE));
			List<String> contatti = contatti(uo);
			if (!contatti.isEmpty()) {
				creditor.setInfoLine1(contatti.get(0));
			}
			if (contatti.size() > 1) {
				creditor.setInfoLine2(contatti.get(1));
			}
		});

		return new DatiCreditore(creditor, logo(versamento.getIdDominio()), logoSecondario(versamento));
	}

	/** Unità operativa della posizione, con fallback su quella che rappresenta l'ente creditore. */
	private Optional<UnitaOperativa> anagrafica(Versamento versamento, DominioEntity dominio) {
		if (versamento.getIdUo() != null) {
			Optional<UnitaOperativa> uo = this.unitaOperativaRepository.findById(versamento.getIdUo());
			if (uo.isPresent()) {
				return uo;
			}
		}
		return this.unitaOperativaRepository.findByIdDominioAndCodUo(dominio.getId(), UnitaOperativa.COD_UO_ENTE_CREDITORE);
	}

	/** Righe di contatto nell'ordine del vecchio {@code infoEnte}. */
	private List<String> contatti(UnitaOperativa uo) {
		List<String> righe = new ArrayList<>();
		if (valorizzato(uo.getUrlSitoWeb())) {
			righe.add(tronca(uo.getUrlSitoWeb(), MAX_INFO_LINE));
		}
		StringBuilder telefoni = new StringBuilder();
		if (valorizzato(uo.getTel())) {
			telefoni.append("Tel: ").append(uo.getTel());
		}
		if (valorizzato(uo.getFax())) {
			if (telefoni.length() > 0) {
				telefoni.append(" - ");
			}
			telefoni.append("Fax: ").append(uo.getFax());
		}
		if (telefoni.length() > 0) {
			righe.add(tronca(telefoni.toString(), MAX_INFO_LINE));
		}
		if (valorizzato(uo.getPec())) {
			righe.add(tronca("pec: " + uo.getPec(), MAX_INFO_LINE));
		} else if (valorizzato(uo.getEmail())) {
			righe.add(tronca("email: " + uo.getEmail(), MAX_INFO_LINE));
		}
		return righe;
	}

	private byte[] logo(Long idDominio) {
		byte[] logoDominio = this.dominioLogoRepository.findById(idDominio)
				.map(DominioLogoEntity::getLogo)
				.filter(l -> l != null && l.length > 0)
				.orElse(null);
		return logoDominio != null ? logoDominio : this.logoDefault;
	}

	/**
	 * Logo del primo dominio diverso da quello della posizione, per le pendenze multibeneficiario
	 * (port di {@code VersamentoUtils.isPendenzaMultibeneficiario} + logo secondario).
	 */
	private byte[] logoSecondario(Versamento versamento) {
		List<SingoloVersamento> voci = this.singoloVersamentoRepository.findByIdVersamento(versamento.getId());
		for (SingoloVersamento voce : voci) {
			Long idDominioVoce = voce.getIdDominio();
			if (idDominioVoce != null && !idDominioVoce.equals(versamento.getIdDominio())) {
				byte[] logo = this.dominioLogoRepository.findById(idDominioVoce)
						.map(DominioLogoEntity::getLogo)
						.filter(l -> l != null && l.length > 0)
						.orElse(null);
				if (logo != null) {
					return logo;
				}
			}
		}
		return null;
	}

	private static boolean valorizzato(String valore) {
		return valore != null && !valore.isBlank();
	}

	private static String tronca(String valore, int lunghezzaMassima) {
		if (valore == null) {
			return null;
		}
		return valore.length() > lunghezzaMassima ? valore.substring(0, lunghezzaMassima) : valore;
	}
}

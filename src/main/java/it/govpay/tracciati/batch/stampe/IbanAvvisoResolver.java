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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import it.govpay.tracciati.batch.entity.IbanAccredito;
import it.govpay.tracciati.batch.entity.SingoloVersamento;
import it.govpay.tracciati.batch.entity.Versamento;
import it.govpay.tracciati.batch.repository.IbanAccreditoRepository;
import it.govpay.tracciati.batch.repository.SingoloVersamentoRepository;
import it.govpay.tracciati.stampe.client.model.Iban;

/**
 * Sceglie l'IBAN da riportare sull'avviso con la procedura della vecchia versione
 * ({@code AvvisoPagamentoUtils.getRata} e {@code AvvisoPagamentoInputConf}): si guarda la
 * <b>prima voce</b> della posizione debitoria e si prende il conto di accredito se è postale,
 * altrimenti il conto di appoggio se è postale.
 *
 * <p>Se nessuno dei due è postale non c'è IBAN da stampare: l'avviso non è un bollettino postale e
 * il servizio di stampa usa i canali di pagamento standard.</p>
 */
@Component
public class IbanAvvisoResolver {

	private final SingoloVersamentoRepository singoloVersamentoRepository;
	private final IbanAccreditoRepository ibanAccreditoRepository;

	public IbanAvvisoResolver(SingoloVersamentoRepository singoloVersamentoRepository,
			IbanAccreditoRepository ibanAccreditoRepository) {
		this.singoloVersamentoRepository = singoloVersamentoRepository;
		this.ibanAccreditoRepository = ibanAccreditoRepository;
	}

	/** IBAN postale della posizione, se presente. */
	public Optional<Iban> ibanPostale(Versamento versamento) {
		return primaVoce(versamento).flatMap(this::ibanPostaleDellaVoce).map(IbanAvvisoResolver::toIban);
	}

	private Optional<SingoloVersamento> primaVoce(Versamento versamento) {
		List<SingoloVersamento> voci = this.singoloVersamentoRepository.findByIdVersamento(versamento.getId());
		return voci.stream().min(Comparator.comparingInt(SingoloVersamento::getIndiceDati));
	}

	private Optional<IbanAccredito> ibanPostaleDellaVoce(SingoloVersamento voce) {
		Optional<IbanAccredito> accredito = postale(voce.getIdIbanAccredito());
		if (accredito.isPresent()) {
			return accredito;
		}
		return postale(voce.getIdIbanAppoggio());
	}

	private Optional<IbanAccredito> postale(Long idIban) {
		if (idIban == null) {
			return Optional.empty();
		}
		return this.ibanAccreditoRepository.findById(idIban).filter(IbanAccredito::isPostale);
	}

	private static Iban toIban(IbanAccredito ibanAccredito) {
		return new Iban()
				.ibanCode(ibanAccredito.getCodIban())
				.ownerBusinessName(ibanAccredito.getIntestatario())
				.postalAuthMessage(ibanAccredito.getAutStampaPoste());
	}
}

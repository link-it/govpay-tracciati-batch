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

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import it.govpay.tracciati.batch.entity.Versamento;
import it.govpay.tracciati.stampe.client.model.Amount;
import it.govpay.tracciati.stampe.client.model.Debtor;
import it.govpay.tracciati.stampe.client.model.Languages;
import it.govpay.tracciati.stampe.client.model.PaymentNotice;

/**
 * Mappa una posizione debitoria ({@link Versamento}) nella richiesta {@link PaymentNotice} del
 * microservizio stampe (endpoint {@code POST /standard}).
 *
 * <p>Dalla posizione debitoria si ricavano debitore e importo (importo, scadenza, numero avviso);
 * i dati del creditore, l'IBAN e la stringa QR pagoPA provengono da {@link DatiAvvisoCreditore}
 * (dominio + IUV).</p>
 *
 * <p>TODO(8): {@code first_logo} è obbligatorio nello schema ma è di tipo {@code File}; la gestione
 * del logo (bytes del dominio → File) sarà cablata nello step di stampa.</p>
 */
@Component
public class PaymentNoticeMapper {

	private static final String TITOLO_DEFAULT = "Avviso di pagamento";

	public PaymentNotice toPaymentNotice(Versamento versamento, DatiAvvisoCreditore dati) {
		Debtor debtor = new Debtor()
				.fiscalCode(versamento.getDebitoreIdentificativo())
				.fullName(versamento.getDebitoreAnagrafica())
				.addressLine1(addressLine1(versamento))
				.addressLine2(addressLine2(versamento));

		Amount full = new Amount()
				.amount(versamento.getImportoTotale())
				.dueDate(versamento.getDataScadenza() != null ? versamento.getDataScadenza().toLocalDate() : null)
				.noticeNumber(versamento.getNumeroAvviso())
				.qrcode(dati.qrcode())
				.iban(dati.iban());

		return new PaymentNotice()
				.language(dati.language() != null ? dati.language() : Languages.IT)
				.title(StringUtils.hasText(dati.title()) ? dati.title() : TITOLO_DEFAULT)
				.firstLogo(dati.logo())
				.creditor(dati.creditor())
				.debtor(debtor)
				.postal(dati.postale() != null ? dati.postale() : Boolean.FALSE)
				.full(full);
	}

	private String addressLine1(Versamento v) {
		return join(" ", v.getDebitoreIndirizzo(), v.getDebitoreCivico());
	}

	private String addressLine2(Versamento v) {
		String cap = v.getDebitoreCap();
		String localita = v.getDebitoreLocalita();
		String provincia = StringUtils.hasText(v.getDebitoreProvincia()) ? "(" + v.getDebitoreProvincia() + ")" : null;
		return join(" ", cap, localita, provincia);
	}

	private String join(String sep, String... parti) {
		StringBuilder sb = new StringBuilder();
		for (String parte : parti) {
			if (StringUtils.hasText(parte)) {
				if (sb.length() > 0) {
					sb.append(sep);
				}
				sb.append(parte.trim());
			}
		}
		return sb.length() == 0 ? null : sb.toString();
	}
}

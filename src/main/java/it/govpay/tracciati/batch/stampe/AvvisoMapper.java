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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import it.govpay.tracciati.batch.dto.LinguaSecondaria;
import it.govpay.tracciati.batch.dto.ProprietaPendenza;
import it.govpay.tracciati.batch.entity.TipoSogliaVersamento;
import it.govpay.tracciati.batch.entity.Versamento;
import it.govpay.tracciati.batch.util.CausaleUtils;
import it.govpay.tracciati.batch.util.CodRataUtils;
import it.govpay.tracciati.stampe.client.model.Amount;
import it.govpay.tracciati.stampe.client.model.CdsViolation;
import it.govpay.tracciati.stampe.client.model.Debtor;
import it.govpay.tracciati.stampe.client.model.Iban;
import it.govpay.tracciati.stampe.client.model.Instalment;
import it.govpay.tracciati.stampe.client.model.Languages;
import it.govpay.tracciati.stampe.client.model.NoticeMetadataSecondLanguage;
import it.govpay.tracciati.stampe.client.model.PaymentNotice;
import it.govpay.tracciati.stampe.client.model.ThresholdPayment;
import it.govpay.tracciati.stampe.client.model.ThresholdType;

/**
 * Compone la richiesta al microservizio di stampa a partire dalle posizioni debitorie di un avviso,
 * portando dalle vecchie utility ({@code AvvisoPagamentoUtils} e {@code AvvisoPagamentoV2Utils}) i
 * dati dell'avviso e lasciando al servizio la composizione delle pagine:
 * <ul>
 *   <li>{@code title} = causale della posizione (decodificata) o descrizione del documento, che sul
 *       template è l'oggetto del pagamento;</li>
 *   <li>{@code full} = rata unica, {@code instalments} = rate numerate,
 *       {@code reduced_payments} = soglie ENTRO/OLTRE;</li>
 *   <li>violazioni CDS su {@code CdsViolation}: importo scontato e ridotto;</li>
 *   <li>{@code second_language} dalla seconda lingua indicata nelle proprietà della pendenza;</li>
 *   <li>data di scadenza con la precedenza di {@code impostaDataScadenza}.</li>
 * </ul>
 */
@Component
public class AvvisoMapper {

	private static final Logger log = LoggerFactory.getLogger(AvvisoMapper.class);

	private final ProprietaPendenzaReader proprietaReader;
	private final Integer giorniValiditaPendenza;

	public AvvisoMapper(ProprietaPendenzaReader proprietaReader,
			@Value("${govpay.tracciati.stampe.giorni-validita-pendenza:#{null}}") Integer giorniValiditaPendenza) {
		this.proprietaReader = proprietaReader;
		this.giorniValiditaPendenza = giorniValiditaPendenza;
	}

	/** Avviso standard: rata unica, rate o pagamenti in forma ridotta. */
	public PaymentNotice toPaymentNotice(AvvisoDaStampare avviso, DatiCreditore creditore,
			ConfigurazioneAvviso configurazione, Map<Long, Iban> ibanPostali) {
		PaymentNotice notice = new PaymentNotice();
		metadati(notice, avviso, creditore, configurazione);

		List<Versamento> rateUniche = configurazione.rateUniche();
		List<Versamento> rate = new ArrayList<>(configurazione.rate());

		if (!rateUniche.isEmpty()) {
			notice.setFull(amount(rateUniche.get(0), creditore, configurazione, ibanPostali));
			if (rateUniche.size() > 1) {
				// il contratto prevede una sola rata unica: le altre restano come rate
				log.warn("Documento {}: {} posizioni in rata unica, le successive sono riportate come rate",
						avviso.numeroDocumento(), rateUniche.size());
				rate.addAll(rateUniche.subList(1, rateUniche.size()));
			}
		}

		boolean conSoglie = !configurazione.soglieTemporali().isEmpty();
		if (!rate.isEmpty() && conSoglie) {
			// il servizio rifiuta rate e pagamenti ridotti insieme: si mantengono le rate
			log.warn("Documento {}: presenti sia rate sia soglie, le {} soglie non sono riportate sull'avviso",
					avviso.numeroDocumento(), configurazione.soglieTemporali().size());
			conSoglie = false;
		}

		if (!rate.isEmpty()) {
			List<Instalment> instalments = new ArrayList<>();
			for (Versamento versamento : rate) {
				instalments.add(instalment(versamento, creditore, configurazione, ibanPostali));
			}
			notice.setInstalments(instalments);
		}

		if (conSoglie) {
			List<ThresholdPayment> ridotti = new ArrayList<>();
			for (Versamento versamento : configurazione.soglieTemporali()) {
				ridotti.add(thresholdPayment(versamento, creditore, configurazione, ibanPostali));
			}
			notice.setReducedPayments(ridotti);
		}

		// le liste del modello generato nascono vuote, non nulle: si azzerano per non inviare campi inutili
		if (notice.getInstalments() != null && notice.getInstalments().isEmpty()) {
			notice.setInstalments(null);
		}
		if (notice.getReducedPayments() != null && notice.getReducedPayments().isEmpty()) {
			notice.setReducedPayments(null);
		}

		return notice;
	}

	/** Avviso per violazione al Codice della Strada: importi scontato e ridotto. */
	public CdsViolation toCdsViolation(AvvisoDaStampare avviso, DatiCreditore creditore,
			ConfigurazioneAvviso configurazione, Map<Long, Iban> ibanPostali) {
		CdsViolation violazione = new CdsViolation();
		metadatiCds(violazione, avviso, creditore, configurazione);
		violazione.setDiscountedAmount(amount(configurazione.scontato(), creditore, configurazione, ibanPostali));
		violazione.setReducedAmount(amount(configurazione.ridotto(), creditore, configurazione, ibanPostali));
		return violazione;
	}

	private void metadati(PaymentNotice notice, AvvisoDaStampare avviso, DatiCreditore creditore,
			ConfigurazioneAvviso configurazione) {
		Versamento principale = avviso.versamentoPrincipale();
		ProprietaPendenza proprieta = this.proprietaReader.leggi(principale);
		String titolo = titolo(avviso);
		notice.setLanguage(Languages.IT);
		notice.setTitle(titolo);
		notice.setSecondLanguage(secondaLingua(proprieta, titolo));
		notice.setFirstLogo(creditore.logo());
		notice.setSecondLogo(creditore.logoSecondario());
		notice.setCreditor(creditore.creditor());
		notice.setDebtor(debitore(principale));
		notice.setPostal(configurazione.postale());
	}

	private void metadatiCds(CdsViolation violazione, AvvisoDaStampare avviso, DatiCreditore creditore,
			ConfigurazioneAvviso configurazione) {
		Versamento principale = avviso.versamentoPrincipale();
		ProprietaPendenza proprieta = this.proprietaReader.leggi(principale);
		String titolo = titolo(avviso);
		violazione.setLanguage(Languages.IT);
		violazione.setTitle(titolo);
		violazione.setSecondLanguage(secondaLingua(proprieta, titolo));
		violazione.setFirstLogo(creditore.logo());
		violazione.setSecondLogo(creditore.logoSecondario());
		violazione.setCreditor(creditore.creditor());
		violazione.setDebtor(debitore(principale));
		violazione.setPostal(configurazione.postale());
	}

	/**
	 * Oggetto del pagamento: descrizione del documento se l'avviso raggruppa più rate, altrimenti la
	 * causale della posizione, che su GovPay è memorizzata in forma codificata.
	 */
	String titolo(AvvisoDaStampare avviso) {
		if (avviso.documento() != null && StringUtils.hasText(avviso.documento().getDescrizione())) {
			return avviso.documento().getDescrizione();
		}
		return CausaleUtils.decodeSimple(avviso.versamentoPrincipale().getCausaleVersamento());
	}

	/**
	 * Seconda lingua dell'avviso: il titolo tradotto è la causale nella seconda lingua indicata nelle
	 * proprietà della pendenza; se manca si ripiega sul titolo italiano, perché il contratto lo
	 * richiede obbligatorio.
	 */
	private NoticeMetadataSecondLanguage secondaLingua(ProprietaPendenza proprieta, String titoloItaliano) {
		LinguaSecondaria lingua = proprieta.getLinguaSecondariaEnum();
		if (lingua == null || !lingua.isBilingue()) {
			return null;
		}
		String titolo = StringUtils.hasText(proprieta.getLinguaSecondariaCausale())
				? proprieta.getLinguaSecondariaCausale()
				: titoloItaliano;
		return new NoticeMetadataSecondLanguage()
				.bilinguism(Boolean.TRUE)
				.language(toLanguage(lingua))
				.title(titolo);
	}

	private static Languages toLanguage(LinguaSecondaria lingua) {
		return switch (lingua) {
			case DE -> Languages.DE;
			case EN -> Languages.EN;
			case FR -> Languages.FR;
			case SL -> Languages.SL;
			case FALSE -> Languages.IT;
		};
	}

	private Amount amount(Versamento versamento, DatiCreditore creditore, ConfigurazioneAvviso configurazione,
			Map<Long, Iban> ibanPostali) {
		return new Amount()
				.amount(versamento.getImportoTotale())
				.dueDate(dataScadenza(versamento))
				.noticeNumber(versamento.getNumeroAvviso())
				.qrcode(qrCode(versamento, creditore))
				.iban(iban(versamento, configurazione, ibanPostali));
	}

	private Instalment instalment(Versamento versamento, DatiCreditore creditore, ConfigurazioneAvviso configurazione,
			Map<Long, Iban> ibanPostali) {
		return new Instalment()
				.amount(versamento.getImportoTotale())
				.dueDate(dataScadenza(versamento))
				.noticeNumber(versamento.getNumeroAvviso())
				.qrcode(qrCode(versamento, creditore))
				.iban(iban(versamento, configurazione, ibanPostali))
				.instalmentNumber(CodRataUtils.da(versamento).numeroRata());
	}

	private ThresholdPayment thresholdPayment(Versamento versamento, DatiCreditore creditore,
			ConfigurazioneAvviso configurazione, Map<Long, Iban> ibanPostali) {
		CodRataUtils.RataSoglia rataSoglia = CodRataUtils.da(versamento);
		return new ThresholdPayment()
				.amount(versamento.getImportoTotale())
				.dueDate(dataScadenza(versamento))
				.noticeNumber(versamento.getNumeroAvviso())
				.qrcode(qrCode(versamento, creditore))
				.iban(iban(versamento, configurazione, ibanPostali))
				.thresholdType(rataSoglia.tipoSoglia() == TipoSogliaVersamento.OLTRE ? ThresholdType.OLTRE : ThresholdType.ENTRO)
				.thresholdDays(rataSoglia.giorniSoglia());
	}

	private static String qrCode(Versamento versamento, DatiCreditore creditore) {
		return QrCodeUtils.buildQrCodePagoPa(versamento.getNumeroAvviso(), creditore.creditor().getFiscalCode(),
				versamento.getImportoTotale());
	}

	/** L'IBAN si riporta solo sugli avvisi postali, dove il servizio lo richiede obbligatorio. */
	private static Iban iban(Versamento versamento, ConfigurazioneAvviso configurazione, Map<Long, Iban> ibanPostali) {
		return configurazione.postale() ? ibanPostali.get(versamento.getId()) : null;
	}

	/**
	 * Data di scadenza da riportare, con la precedenza di {@code AvvisoPagamentoUtils.impostaDataScadenza}:
	 * data indicata nelle proprietà, data di validità, data di scadenza, infine data di creazione più
	 * i giorni di validità configurati.
	 */
	LocalDate dataScadenza(Versamento versamento) {
		ProprietaPendenza proprieta = this.proprietaReader.leggi(versamento);
		LocalDate daProprieta = toLocalDate(proprieta.getDataScandenzaAvviso());
		if (daProprieta != null) {
			return daProprieta;
		}
		if (versamento.getDataValidita() != null) {
			return versamento.getDataValidita().toLocalDate();
		}
		if (versamento.getDataScadenza() != null) {
			return versamento.getDataScadenza().toLocalDate();
		}
		if (this.giorniValiditaPendenza != null && versamento.getDataCreazione() != null) {
			return versamento.getDataCreazione().toLocalDate().plusDays(this.giorniValiditaPendenza.longValue());
		}
		return null;
	}

	private static LocalDate toLocalDate(String valore) {
		if (!StringUtils.hasText(valore)) {
			return null;
		}
		String testo = valore.trim();
		try {
			return LocalDate.parse(testo);
		} catch (RuntimeException e) {
			log.trace("Data scadenza avviso [{}] non in formato data: {}", testo, e.getMessage());
		}
		try {
			return LocalDateTime.parse(testo).toLocalDate();
		} catch (RuntimeException e) {
			log.trace("Data scadenza avviso [{}] non in formato data/ora: {}", testo, e.getMessage());
		}
		try {
			return OffsetDateTime.parse(testo).toLocalDate();
		} catch (RuntimeException e) {
			log.warn("Data scadenza avviso [{}] non interpretabile: si usa la data della posizione", testo);
			return null;
		}
	}

	private static Debtor debitore(Versamento versamento) {
		return new Debtor()
				.fiscalCode(versamento.getDebitoreIdentificativo())
				.fullName(versamento.getDebitoreAnagrafica())
				.addressLine1(indirizzo(versamento))
				.addressLine2(localita(versamento));
	}

	private static String indirizzo(Versamento versamento) {
		return unisci(versamento.getDebitoreIndirizzo(), versamento.getDebitoreCivico());
	}

	private static String localita(Versamento versamento) {
		String provincia = StringUtils.hasText(versamento.getDebitoreProvincia())
				? "(" + versamento.getDebitoreProvincia() + ")"
				: null;
		return unisci(versamento.getDebitoreCap(), versamento.getDebitoreLocalita(), provincia);
	}

	private static String unisci(String... parti) {
		StringBuilder sb = new StringBuilder();
		for (String parte : parti) {
			if (StringUtils.hasText(parte)) {
				if (sb.length() > 0) {
					sb.append(' ');
				}
				sb.append(parte.trim());
			}
		}
		return sb.length() == 0 ? null : sb.toString();
	}
}

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
package it.govpay.tracciati.batch.converter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Component;

import it.govpay.tracciati.batch.dto.PendenzaPost;
import it.govpay.tracciati.batch.dto.Soggetto;
import it.govpay.tracciati.batch.dto.VocePendenza;
import it.govpay.tracciati.batch.entity.SingoloVersamento;
import it.govpay.tracciati.batch.entity.Versamento;

/**
 * Conversione della pendenza di input ({@link PendenzaPost}) nell'entità {@link Versamento}
 * (+ voci in {@link SingoloVersamento}). Port della logica di mappatura di
 * {@code it.govpay.core.utils.TracciatiConverter} della procedura legacy.
 *
 * <p>NOTA: gli identificativi delle anagrafiche (id_dominio, id_applicazione, id_tipo_versamento,
 * id_tipo_versamento_dominio), l'IUV e il numero avviso NON sono impostati qui: sono
 * responsabilità di {@code CaricamentoService} (risoluzione anagrafica + generazione IUV).</p>
 *
 * <p>TODO(5c): confermare rispetto a {@code Versamento.caricaVersamento} legacy i valori di default
 * delle colonne NOT NULL enumerate (statoVersamento, statoPagamento, tipo): i letterali usati qui
 * sono la scelta più probabile ma vanno validati contro il dominio enum di GovPay.</p>
 */
@Component
public class PendenzaConverter {

	private static final String DEBITORE_ANONIMO = "ANONIMO";
	private static final String STATO_VERSAMENTO_NON_ESEGUITO = "NON_ESEGUITO"; // TODO confermare
	private static final String STATO_PAGAMENTO_NON_PAGATO = "NON_PAGATO";       // TODO confermare
	private static final String TIPO_VERSAMENTO_DOVUTO = "DOVUTO";               // TODO confermare
	private static final String STATO_SINGOLO_NON_ESEGUITO = "NON_ESEGUITO";     // TODO confermare

	public Versamento toVersamento(PendenzaPost pendenza) {
		LocalDateTime now = LocalDateTime.now();
		Versamento v = new Versamento();

		v.setCodVersamentoEnte(pendenza.getIdPendenza());
		v.setNome(pendenza.getNome());
		v.setCausaleVersamento(pendenza.getCausale());

		BigDecimal importo = pendenza.getImporto() != null ? pendenza.getImporto() : sommaVoci(pendenza.getVoci());
		v.setImportoTotale(importo != null ? importo.doubleValue() : 0d);

		Soggetto s = pendenza.getSoggettoPagatore();
		v.setDebitoreTipo(s != null ? s.getTipo() : null);
		v.setDebitoreIdentificativo(valoreOAnonimo(s != null ? s.getIdentificativo() : null));
		v.setDebitoreAnagrafica(valoreOAnonimo(s != null ? s.getAnagrafica() : null));
		if (s != null) {
			v.setDebitoreIndirizzo(s.getIndirizzo());
			v.setDebitoreCivico(s.getCivico());
			v.setDebitoreCap(s.getCap());
			v.setDebitoreLocalita(s.getLocalita());
			v.setDebitoreProvincia(s.getProvincia());
			v.setDebitoreNazione(s.getNazione());
			v.setDebitoreEmail(s.getEmail());
			v.setDebitoreTelefono(s.getTelefono());
			v.setDebitoreCellulare(s.getCellulare());
			v.setDebitoreFax(s.getFax());
		}

		v.setTassonomia(pendenza.getTassonomia());
		v.setTassonomiaAvviso(pendenza.getTassonomiaAvviso());
		v.setNumeroAvviso(pendenza.getNumeroAvviso());
		if (pendenza.getAnnoRiferimento() != null) {
			v.setCodAnnoTributario(String.valueOf(pendenza.getAnnoRiferimento().intValue()));
		}
		v.setDataValidita(toLocalDateTime(pendenza.getDataValidita()));
		v.setDataScadenza(toLocalDateTime(pendenza.getDataScadenza()));
		v.setDirezione(pendenza.getDirezione());
		v.setDivisione(pendenza.getDivisione());

		v.setDataCreazione(now);
		v.setDataOraUltimoAggiornamento(now);
		v.setSrcDebitoreIdentificativo(v.getDebitoreIdentificativo());

		// Default colonne NOT NULL (vedi TODO in classdoc)
		v.setStatoVersamento(STATO_VERSAMENTO_NON_ESEGUITO);
		v.setStatoPagamento(STATO_PAGAMENTO_NON_PAGATO);
		v.setTipo(TIPO_VERSAMENTO_DOVUTO);
		v.setAggiornabile(true);
		v.setAck(false);
		v.setAnomalo(false);
		v.setImportoPagato(0d);
		v.setImportoIncassato(0d);

		return v;
	}

	public List<SingoloVersamento> toSingoliVersamenti(PendenzaPost pendenza) {
		List<SingoloVersamento> singoli = new ArrayList<>();
		List<VocePendenza> voci = pendenza.getVoci();
		if (voci == null) {
			return singoli;
		}
		int indice = 1;
		for (VocePendenza voce : voci) {
			SingoloVersamento sv = new SingoloVersamento();
			sv.setCodSingoloVersamentoEnte(voce.getIdVocePendenza());
			sv.setStatoSingoloVersamento(STATO_SINGOLO_NON_ESEGUITO);
			sv.setImportoSingoloVersamento(voce.getImporto() != null ? voce.getImporto().doubleValue() : 0d);
			sv.setDescrizione(voce.getDescrizione());
			sv.setDescrizioneCausaleRpt(voce.getDescrizioneCausaleRPT());
			sv.setTipoContabilita(voce.getTipoContabilita());
			sv.setCodiceContabilita(voce.getCodiceContabilita());
			sv.setTipoBollo(voce.getTipoBollo());
			sv.setHashDocumento(voce.getHashDocumento());
			sv.setProvinciaResidenza(voce.getProvinciaResidenza());
			sv.setIndiceDati(indice++);
			singoli.add(sv);
		}
		return singoli;
	}

	private BigDecimal sommaVoci(List<VocePendenza> voci) {
		if (voci == null || voci.isEmpty()) {
			return null;
		}
		BigDecimal totale = BigDecimal.ZERO;
		for (VocePendenza voce : voci) {
			if (voce.getImporto() != null) {
				totale = totale.add(voce.getImporto());
			}
		}
		return totale;
	}

	private String valoreOAnonimo(String value) {
		return (value == null || value.trim().isEmpty()) ? DEBITORE_ANONIMO : value;
	}

	private LocalDateTime toLocalDateTime(Date date) {
		return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
	}
}

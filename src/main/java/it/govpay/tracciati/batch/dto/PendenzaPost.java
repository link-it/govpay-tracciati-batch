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
package it.govpay.tracciati.batch.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.Data;

/**
 * Pendenza da inserire (payload di input del tracciato). I nomi dei campi ricalcano
 * quelli del modello REST legacy ({@code it.govpay.core.beans.tracciati.PendenzaPost})
 * per compatibilità con i tracciati JSON prodotti dal sistema.
 */
@Data
public class PendenzaPost {
	private String idA2A;
	private String idPendenza;
	private String idDominio;
	private String idUnitaOperativa;
	private String idTipoPendenza;
	private String nome;
	private String causale;
	private Soggetto soggettoPagatore;
	private BigDecimal importo;
	private String numeroAvviso;
	private Date dataCaricamento;
	private Date dataValidita;
	private Date dataScadenza;
	private BigDecimal annoRiferimento;
	private String cartellaPagamento;
	private Object datiAllegati;
	private String tassonomia;
	private String tassonomiaAvviso;
	private String direzione;
	private String divisione;
	private DocumentoPendenza documento;
	private Object proprieta;
	private List<VocePendenza> voci = new ArrayList<>();
	private List<Object> allegati;
}

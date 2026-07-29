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

import java.util.Date;

import lombok.Data;

/**
 * Bean di stato serializzato in JSON nel campo {@code tracciati.bean_dati}.
 *
 * <p>I nomi dei campi ricalcano quelli della procedura legacy
 * ({@code it.govpay.core.beans.tracciati.TracciatoPendenza}) per garantire la
 * compatibilità con la console GovPay, che legge questo JSON per mostrare stato e
 * avanzamento all'utente (decisione D6).</p>
 */
@Data
public class TracciatoPendenza {

	// Contatori operazioni di inserimento (ADD)
	private long numAddTotali;
	private long numAddOk;
	private long numAddKo;
	/** Checkpoint: indice riga corrente nell'elaborazione delle ADD. */
	private long lineaElaborazioneAdd;

	// Contatori operazioni di annullamento (DEL)
	private long numDelTotali;
	private long numDelOk;
	private long numDelKo;
	/** Checkpoint: indice riga corrente nell'elaborazione delle DEL. */
	private long lineaElaborazioneDel;

	private Date dataUltimoAggiornamento;

	/** Stato fine di avanzamento (valori di {@link StatoTracciatoType}). */
	private String stepElaborazione;
	private String descrizioneStepElaborazione;

	private Boolean avvisaturaAbilitata;
	private String avvisaturaModalita;

	// Contatori stampe avvisi
	private long numStampeTotali;
	private long numStampeOk;
	private long numStampeKo;

	/** Se generare la stampa degli avvisi al termine del caricamento. */
	private boolean stampaAvvisi;
}

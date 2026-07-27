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
package it.govpay.tracciati.batch.service;

/**
 * Generatore del progressivo IUV per (dominio + prefisso), con stato persistente su DB.
 *
 * <p><b>Decisione aperta (5c):</b> la procedura legacy usa
 * {@code org.openspcoop2.utils.id.serial.IDSerialGenerator} (non presente sul classpath del batch).
 * Va deciso se aggiungere la dipendenza {@code openspcoop2-utils} per riusare esattamente lo stesso
 * generatore, oppure implementare un progressivo nativo su DB (sequenza/tabella dedicata con
 * accesso serializzabile). L'IUV completo è poi formattato da
 * {@link it.govpay.tracciati.batch.util.IuvCheckDigitUtils#formattaIuv}.</p>
 */
public interface IuvProgressivoService {

	/**
	 * Prossimo progressivo per la combinazione dominio+prefisso.
	 *
	 * @param codDominioPrefix informazione associata al progressivo (codDominio + prefisso IUV)
	 * @return progressivo univoco crescente
	 */
	long prossimoProgressivo(String codDominioPrefix);
}

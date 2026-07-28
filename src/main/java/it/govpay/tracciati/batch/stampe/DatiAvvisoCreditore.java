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

import it.govpay.tracciati.stampe.client.model.Creditor;
import it.govpay.tracciati.stampe.client.model.Iban;
import it.govpay.tracciati.stampe.client.model.Languages;

/**
 * Dati dell'avviso derivati dal dominio/IUV (non ricavabili dalla sola posizione debitoria):
 * ente creditore, IBAN, stringa QR pagoPA, lingua, titolo, flag bollettino postale.
 *
 * <p>Sono risolti dal dominio (ragione sociale, codice fiscale/IBAN, loghi) e dalla generazione
 * IUV (qrcode) in fase di caricamento/stampa; qui sono passati come input al mapper.</p>
 */
public record DatiAvvisoCreditore(
		Creditor creditor,
		Iban iban,
		String qrcode,
		Languages language,
		String title,
		Boolean postale) {
}

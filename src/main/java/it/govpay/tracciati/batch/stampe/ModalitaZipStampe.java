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

/**
 * Modalità di persistenza dello ZIP degli avvisi sulla colonna {@code tracciati.zip_stampe}, la cui
 * tipizzazione dipende dal vendor del database GovPay (port della gestione per tipo database della
 * procedura legacy {@code Tracciati.salvaZipStampeTracciato}):
 * <ul>
 *   <li>PostgreSQL: {@code OID}, cioè un <i>Large Object</i> scritto con la {@code LargeObjectManager} API;</li>
 *   <li>Oracle ({@code BLOB}), MySQL ({@code MEDIUMBLOB}), SQL Server ({@code VARBINARY(MAX)}),
 *       HSQL/H2 ({@code VARBINARY}): {@code Blob} JDBC standard.</li>
 * </ul>
 */
public enum ModalitaZipStampe {

	/** Rilevamento automatico dal vendor della connessione (default). */
	AUTO,

	/** Large Object PostgreSQL: la colonna contiene l'OID. */
	LARGE_OBJECT,

	/** Blob JDBC standard: la colonna contiene i byte. */
	BLOB
}

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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.stereotype.Service;

import it.govpay.tracciati.batch.config.BatchProperties;

/**
 * Implementazione JDBC di {@link ZipStampeStore} con gestione dei diversi tipi di database, portata
 * dalla procedura legacy ({@code it.govpay.core.business.Tracciati}):
 * <ul>
 *   <li>PostgreSQL: {@code zip_stampe} è un {@code OID}, lo ZIP viene scritto su un <i>Large
 *       Object</i> e nella colonna finisce l'OID (vedi {@link PostgresLargeObjectSupport});</li>
 *   <li>Oracle / MySQL / SQL Server / HSQL / H2: {@code Blob} JDBC creato dalla connessione e
 *       valorizzato in streaming.</li>
 * </ul>
 *
 * <p>In entrambi i casi lo ZIP è prodotto <b>direttamente sullo stream verso il database</b>, senza
 * copia intermedia in memoria (è l'ottimizzazione presente nel vecchio flusso).</p>
 *
 * <p>Usa una connessione dedicata con {@code autoCommit=false} — indispensabile per i Large Object
 * PostgreSQL, che vivono solo all'interno di una transazione — e la committa al termine della
 * scrittura. Lo stato del tracciato viene aggiornato subito dopo, nello step di finalizzazione: se la
 * scrittura dello ZIP fallisce il tracciato resta {@code IN_STAMPA} e viene ripreso alla successiva
 * esecuzione.</p>
 */
@Service
public class ZipStampeStoreJdbc implements ZipStampeStore {

	private static final Logger log = LoggerFactory.getLogger(ZipStampeStoreJdbc.class);

	static final String SQL_UPDATE_ZIP_STAMPE = "UPDATE tracciati SET zip_stampe = ? WHERE id = ?";
	static final String SQL_SELECT_ZIP_STAMPE = "SELECT zip_stampe FROM tracciati WHERE id = ?";

	private final DataSource dataSource;
	private final ModalitaZipStampe modalitaConfigurata;

	public ZipStampeStoreJdbc(DataSource dataSource, BatchProperties batchProperties) {
		this.dataSource = dataSource;
		this.modalitaConfigurata = batchProperties.getModalitaZipStampe();
	}

	@Override
	public long scrivi(long idTracciato, ProduttoreZip produttore) {
		try (Connection con = this.dataSource.getConnection()) {
			// i Large Object PostgreSQL sono validi solo dentro una transazione (come nel legacy)
			con.setAutoCommit(false);
			try {
				ModalitaZipStampe modalita = risolviModalita(con);
				log.debug("Persistenza ZIP stampe del tracciato {} in modalità {}", idTracciato, modalita);
				long byteScritti = switch (modalita) {
					case LARGE_OBJECT -> PostgresLargeObjectSupport.scrivi(con, idTracciato, produttore);
					case BLOB -> scriviBlob(con, idTracciato, produttore);
					case AUTO -> throw new IllegalStateException("Modalità di persistenza dello ZIP stampe non risolta");
				};
				con.commit();
				return byteScritti;
			} catch (Exception e) {
				con.rollback();
				throw e;
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Errore durante la produzione dello ZIP delle stampe del tracciato " + idTracciato, e);
		} catch (SQLException e) {
			throw new IllegalStateException("Errore durante la scrittura dello ZIP delle stampe del tracciato " + idTracciato, e);
		}
	}

	/**
	 * Risolve la modalità da usare: quella configurata, se esplicita, altrimenti il vendor della
	 * connessione (equivalente allo {@code switch} su {@code TipiDatabase} del vecchio codice).
	 */
	private ModalitaZipStampe risolviModalita(Connection con) throws SQLException {
		if (this.modalitaConfigurata != ModalitaZipStampe.AUTO) {
			return this.modalitaConfigurata;
		}
		String prodotto = con.getMetaData().getDatabaseProductName();
		DatabaseDriver database = DatabaseDriver.fromProductName(prodotto);
		return switch (database) {
			case POSTGRESQL -> ModalitaZipStampe.LARGE_OBJECT;
			case ORACLE, MYSQL, MARIADB, SQLSERVER, HSQLDB, H2 -> ModalitaZipStampe.BLOB;
			default -> throw new IllegalStateException(
					"Tipo database [" + prodotto + "] non gestito per la persistenza di tracciati.zip_stampe");
		};
	}

	/** Scrittura su {@code Blob} JDBC (Oracle, MySQL, SQL Server, HSQL, H2). */
	private long scriviBlob(Connection con, long idTracciato, ProduttoreZip produttore) throws SQLException, IOException {
		Blob blob = con.createBlob();
		try {
			long byteScritti;
			try (ContatoreOutputStream out = new ContatoreOutputStream(blob.setBinaryStream(1))) {
				produttore.scrivi(out);
				byteScritti = out.getByteScritti();
			}
			try (PreparedStatement ps = con.prepareStatement(SQL_UPDATE_ZIP_STAMPE)) {
				ps.setBlob(1, blob);
				ps.setLong(2, idTracciato);
				ps.executeUpdate();
			}
			return byteScritti;
		} finally {
			try {
				blob.free();
			} catch (SQLException e) {
				log.debug("Rilascio del Blob delle stampe non riuscito: {}", e.getMessage());
			}
		}
	}
}

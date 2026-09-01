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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.postgresql.PGConnection;
import org.postgresql.largeobject.LargeObject;
import org.postgresql.largeobject.LargeObjectManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scrittura dello ZIP delle stampe come <i>Large Object</i> PostgreSQL, dove {@code tracciati.zip_stampe}
 * è di tipo {@code OID} (port di {@code Tracciati.elaboraTracciato}, ramo {@code case POSTGRESQL}).
 *
 * <p>Le classi del driver PostgreSQL sono referenziate solo qui, così che la classe venga caricata
 * unicamente quando il database è effettivamente PostgreSQL.</p>
 *
 * <p>Rispetto al legacy: l'unwrap della connessione usa {@link Connection#unwrap(Class)} (il pool è
 * HikariCP, non serve la reflection su {@code getUnderlyingConnection} delle installazioni JBoss) e
 * l'eventuale Large Object già presente sulla riga viene rimosso con {@code lo_unlink} dopo
 * l'aggiornamento, per non lasciare oggetti orfani a ogni ripresa del tracciato (nel vecchio flusso
 * restavano fino allo svecchiamento).</p>
 */
final class PostgresLargeObjectSupport {

	private static final Logger log = LoggerFactory.getLogger(PostgresLargeObjectSupport.class);

	private PostgresLargeObjectSupport() {
		// classe di utilità
	}

	static long scrivi(Connection con, long idTracciato, ZipStampeStore.ProduttoreZip produttore) throws SQLException, IOException {
		LargeObjectManager largeObjectManager = getLargeObjectApi(con);
		Long oidPrecedente = leggiOid(con, idTracciato);

		long oid = largeObjectManager.createLO(LargeObjectManager.WRITE);
		long byteScritti;
		try (LargeObject largeObject = largeObjectManager.open(oid, LargeObjectManager.WRITE);
				ContatoreOutputStream out = new ContatoreOutputStream(largeObject.getOutputStream())) {
			produttore.scrivi(out);
			byteScritti = out.getByteScritti();
		}

		try (PreparedStatement ps = con.prepareStatement(ZipStampeStoreJdbc.SQL_UPDATE_ZIP_STAMPE)) {
			ps.setLong(1, oid);
			ps.setLong(2, idTracciato);
			ps.executeUpdate();
		}

		if (oidPrecedente != null) {
			// rimozione transazionale del large object sostituito (la riga ora punta al nuovo oid)
			largeObjectManager.unlink(oidPrecedente.longValue());
			log.debug("Rimosso il large object {} sostituito sul tracciato {}", oidPrecedente, idTracciato);
		}
		return byteScritti;
	}

	private static LargeObjectManager getLargeObjectApi(Connection con) throws SQLException {
		PGConnection pgConnection = con.isWrapperFor(PGConnection.class)
				? con.unwrap(PGConnection.class)
				: (PGConnection) con;
		return pgConnection.getLargeObjectAPI();
	}

	/** OID eventualmente già presente sulla riga (tracciato ripreso o rielaborato). */
	private static Long leggiOid(Connection con, long idTracciato) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement(ZipStampeStoreJdbc.SQL_SELECT_ZIP_STAMPE)) {
			ps.setLong(1, idTracciato);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					long oid = rs.getLong(1);
					return rs.wasNull() ? null : oid;
				}
				return null;
			}
		}
	}
}

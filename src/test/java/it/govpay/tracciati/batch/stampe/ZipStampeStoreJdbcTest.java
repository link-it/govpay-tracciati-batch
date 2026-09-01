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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import it.govpay.tracciati.batch.config.BatchProperties;
import it.govpay.tracciati.batch.dto.RisultatoStampa;

/**
 * Verifica la persistenza dello ZIP delle stampe sul ramo {@code Blob} JDBC (su H2, come per
 * HSQL/Oracle/MySQL/SQL Server): rilevamento automatico della modalità, scrittura in streaming e
 * rollback in caso di errore durante la produzione.
 */
class ZipStampeStoreJdbcTest {

	private static final byte[] PDF = {1, 2, 3};
	private static final long ID_TRACCIATO = 42L;

	private DataSource dataSource;
	private ZipStampeStore store;

	@BeforeEach
	void setUp() throws Exception {
		this.dataSource = new DriverManagerDataSource(
				"jdbc:h2:mem:zipstampe-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1", "sa", "");
		try (Connection con = this.dataSource.getConnection(); Statement st = con.createStatement()) {
			st.execute("CREATE TABLE tracciati (id BIGINT PRIMARY KEY, zip_stampe VARBINARY(16777215))");
			st.execute("INSERT INTO tracciati (id) VALUES (" + ID_TRACCIATO + ")");
		}
		this.store = new ZipStampeStoreJdbc(this.dataSource, new BatchProperties());
	}

	@Test
	void scriveLoZipSuZipStampe() throws Exception {
		long byteScritti = this.store.scrivi(ID_TRACCIATO, destinazione -> {
			ZipStampeBuilder builder = new ZipStampeBuilder(destinazione);
			builder.aggiungi(RisultatoStampa.ok(PDF, "D01", "AV1", null));
			builder.aggiungi(RisultatoStampa.ok(PDF, "D01", "AV2", null));
			builder.chiudi();
		});

		byte[] zip = leggiZipStampe();
		assertEquals(byteScritti, zip.length);
		List<String> nomi = entryNames(zip);
		assertEquals(2, nomi.size());
		assertTrue(nomi.contains("D01_AV1.pdf"));
		assertTrue(nomi.contains("D01_AV2.pdf"));
	}

	@Test
	void erroreDiProduzioneLasciaLaColonnaInvariata() throws Exception {
		assertThrows(UncheckedIOException.class, () -> this.store.scrivi(ID_TRACCIATO, destinazione -> {
			destinazione.write(PDF);
			throw new IOException("errore simulato di produzione");
		}));

		assertNull(leggiZipStampe());
	}

	private byte[] leggiZipStampe() throws Exception {
		try (Connection con = this.dataSource.getConnection();
				Statement st = con.createStatement();
				ResultSet rs = st.executeQuery("SELECT zip_stampe FROM tracciati WHERE id = " + ID_TRACCIATO)) {
			return rs.next() ? rs.getBytes(1) : null;
		}
	}

	private List<String> entryNames(byte[] zip) throws Exception {
		List<String> nomi = new ArrayList<>();
		try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				nomi.add(entry.getName());
			}
		}
		return nomi;
	}
}

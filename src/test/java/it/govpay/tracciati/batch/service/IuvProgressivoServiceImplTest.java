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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class IuvProgressivoServiceImplTest {

	private DataSource dataSource;

	@BeforeEach
	void setUp() throws Exception {
		String url = "jdbc:h2:mem:iuv" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
		this.dataSource = new DriverManagerDataSource(url, "sa", "");
		try (Connection con = this.dataSource.getConnection(); Statement st = con.createStatement()) {
			st.execute("CREATE TABLE ID_MESSAGGIO_RELATIVO ("
					+ "COUNTER BIGINT NOT NULL, PROTOCOLLO VARCHAR(255) NOT NULL, INFO_ASSOCIATA VARCHAR(255) NOT NULL, "
					+ "CONSTRAINT pk_id_msg_rel PRIMARY KEY (PROTOCOLLO, INFO_ASSOCIATA))");
		}
	}

	@Test
	void progressivoMonotonoConRiservaABlocchi() throws Exception {
		IuvProgressivoServiceImpl service = new IuvProgressivoServiceImpl(this.dataSource, new IuvProgressivoBuffer());

		assertEquals(1L, service.prossimoProgressivo("DOM01@N"));
		assertEquals(2L, service.prossimoProgressivo("DOM01@N"));
		assertEquals(3L, service.prossimoProgressivo("DOM01@N"));

		// alla creazione viene inserito COUNTER=1; alla seconda chiamata si riservano 100 valori (2..101)
		assertEquals(101L, leggiCounter("DOM01@N"));
	}

	@Test
	void infoAssociateDiverseHannoProgressiviIndipendenti() {
		IuvProgressivoServiceImpl service = new IuvProgressivoServiceImpl(this.dataSource, new IuvProgressivoBuffer());

		assertEquals(1L, service.prossimoProgressivo("DOM_A@N"));
		assertEquals(1L, service.prossimoProgressivo("DOM_B@N"));
		assertEquals(2L, service.prossimoProgressivo("DOM_A@N"));
		assertEquals(2L, service.prossimoProgressivo("DOM_B@N"));
	}

	private long leggiCounter(String infoAssociata) throws Exception {
		try (Connection con = this.dataSource.getConnection();
				PreparedStatement ps = con.prepareStatement("SELECT COUNTER FROM ID_MESSAGGIO_RELATIVO WHERE PROTOCOLLO='GovPay' AND INFO_ASSOCIATA=?")) {
			ps.setString(1, infoAssociata);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getLong(1);
			}
		}
	}
}

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

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Generatore del progressivo IUV con stato su DB, port <b>nativo</b> (senza dipendenza openspcoop2)
 * dell'algoritmo di {@code IDSerialGenerator_numeric.generate} di GovWay.
 *
 * <p>Riusa la tabella esistente {@code ID_MESSAGGIO_RELATIVO} (colonne {@code COUNTER},
 * {@code PROTOCOLLO}, {@code INFO_ASSOCIATA}, PK {@code (PROTOCOLLO, INFO_ASSOCIATA)}) con:
 * connessione dedicata (indipendente dalla transazione JPA del batch), isolamento
 * {@code SERIALIZABLE}, {@code SELECT ... FOR UPDATE} + insert/update + commit, loop di retry con
 * attesa random crescente. Riserva un blocco di {@link #SIZE_BUFFER} valori per giro, bufferizzando
 * i residui in {@link IuvProgressivoBuffer}.</p>
 */
@Service
public class IuvProgressivoServiceImpl implements IuvProgressivoService {

	private static final Logger log = LoggerFactory.getLogger(IuvProgressivoServiceImpl.class);

	private static final String TABELLA = "ID_MESSAGGIO_RELATIVO";
	private static final String PROTOCOLLO = "GovPay";
	private static final int SIZE_BUFFER = 100;
	private static final boolean WRAP = false;
	private static final long MAX_VALUE = Long.MAX_VALUE;

	// Parametri del loop serializable (allineati ai default GovWay)
	private static final long TIMEOUT_MS = 60000L;
	private static final int NEXT_INTERVAL_MS = 100;
	private static final boolean INCREMENT_MODE = true;
	private static final int INCREMENT_MS = 200;
	private static final int MAX_INTERVAL_MS = 2000;

	private static final String SELECT_FOR_UPDATE = "SELECT COUNTER FROM " + TABELLA
			+ " WHERE PROTOCOLLO=? AND INFO_ASSOCIATA=? FOR UPDATE";
	private static final String SELECT_SQLSERVER = "SELECT COUNTER FROM " + TABELLA
			+ " WITH (UPDLOCK, HOLDLOCK) WHERE PROTOCOLLO=? AND INFO_ASSOCIATA=?";
	private static final String INSERT = "INSERT INTO " + TABELLA + " (COUNTER, PROTOCOLLO, INFO_ASSOCIATA) VALUES (?, ?, ?)";
	private static final String UPDATE = "UPDATE " + TABELLA + " SET COUNTER=? WHERE PROTOCOLLO=? AND INFO_ASSOCIATA=?";

	private final DataSource dataSource;
	private final IuvProgressivoBuffer buffer;
	private final SecureRandom random = new SecureRandom();

	public IuvProgressivoServiceImpl(DataSource dataSource, IuvProgressivoBuffer buffer) {
		this.dataSource = dataSource;
		this.buffer = buffer;
	}

	@Override
	public long prossimoProgressivo(String infoAssociata) {
		Long fromBuffer = this.buffer.nextValue(infoAssociata);
		if (fromBuffer != null) {
			return fromBuffer;
		}
		return generaDaDb(infoAssociata);
	}

	private long generaDaDb(String infoAssociata) {
		long scadenza = System.currentTimeMillis() + TIMEOUT_MS;
		int iterazione = 0;
		List<Long> generati = new ArrayList<>();
		boolean ok = false;

		while (!ok && System.currentTimeMillis() < scadenza) {
			// un altro thread potrebbe aver riempito il buffer nel frattempo
			Long fromBuffer = this.buffer.nextValue(infoAssociata);
			if (fromBuffer != null) {
				return fromBuffer;
			}

			iterazione++;
			generati = new ArrayList<>();
			try (Connection con = this.dataSource.getConnection()) {
				con.setAutoCommit(false);
				con.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
				try {
					Long counterAttuale = leggiConLock(con, infoAssociata);
					if (counterAttuale == null) {
						inserisci(con, infoAssociata);
						generati.add(1L);
					} else {
						long counter = counterAttuale;
						for (int i = 0; i < SIZE_BUFFER; i++) {
							if (counter + 1 > MAX_VALUE) {
								if (WRAP) {
									counter = 0;
								} else if (generati.isEmpty()) {
									throw new IllegalStateException("Superato il numero massimo di IUV generabili");
								} else {
									break;
								}
							}
							counter++;
							generati.add(counter);
						}
						aggiorna(con, infoAssociata, counter);
					}
					con.commit();
					ok = true;
				} catch (Exception e) {
					con.rollback();
					throw e;
				}
			} catch (Exception e) {
				log.debug("Tentativo {} di generazione progressivo IUV per [{}] fallito: {}", iterazione, infoAssociata, e.getMessage());
			}

			if (!ok) {
				attesaRandom(iterazione);
			}
		}

		if (!ok || generati.isEmpty()) {
			throw new IllegalStateException("Generazione del progressivo IUV non riuscita per [" + infoAssociata + "] (accesso serializable)");
		}

		long risultato = generati.remove(0);
		if (!generati.isEmpty()) {
			this.buffer.putAll(infoAssociata, generati);
		}
		return risultato;
	}

	private Long leggiConLock(Connection con, String infoAssociata) throws Exception {
		String sql = isSqlServer(con) ? SELECT_SQLSERVER : SELECT_FOR_UPDATE;
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, PROTOCOLLO);
			ps.setString(2, infoAssociata);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getLong("COUNTER") : null;
			}
		}
	}

	private void inserisci(Connection con, String infoAssociata) throws Exception {
		try (PreparedStatement ps = con.prepareStatement(INSERT)) {
			ps.setLong(1, 1L);
			ps.setString(2, PROTOCOLLO);
			ps.setString(3, infoAssociata);
			ps.execute();
		}
	}

	private void aggiorna(Connection con, String infoAssociata, long counter) throws Exception {
		try (PreparedStatement ps = con.prepareStatement(UPDATE)) {
			ps.setLong(1, counter);
			ps.setString(2, PROTOCOLLO);
			ps.setString(3, infoAssociata);
			ps.execute();
		}
	}

	private boolean isSqlServer(Connection con) throws Exception {
		String prodotto = con.getMetaData().getDatabaseProductName();
		return prodotto != null && prodotto.toLowerCase().contains("sql server");
	}

	private void attesaRandom(int iterazione) {
		int intervallo = NEXT_INTERVAL_MS;
		if (INCREMENT_MODE) {
			intervallo += iterazione * INCREMENT_MS;
			if (intervallo > MAX_INTERVAL_MS) {
				intervallo = MAX_INTERVAL_MS;
			}
		}
		try {
			Thread.sleep(this.random.nextInt(intervallo));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}

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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

import it.govpay.tracciati.batch.dto.RisultatoStampa;

class ZipStampeBuilderTest {

	private static final byte[] PDF = {1, 2, 3};

	@Test
	void deduplicaDocumentiENumeriAvviso() throws Exception {
		ByteArrayOutputStream destinazione = new ByteArrayOutputStream();
		ZipStampeBuilder builder = new ZipStampeBuilder(destinazione);
		// due rate dello stesso documento -> una sola copia
		builder.aggiungi(RisultatoStampa.ok(PDF, "D01", "A1", "DOC1"));
		builder.aggiungi(RisultatoStampa.ok(PDF, "D01", "A2", "DOC1"));
		// avviso senza documento
		builder.aggiungi(RisultatoStampa.ok(PDF, "D01", "AV9", null));
		// duplicato del numero avviso -> saltato
		builder.aggiungi(RisultatoStampa.ok(PDF, "D01", "AV9", null));
		// esito KO -> saltato
		builder.aggiungi(RisultatoStampa.ko("errore"));

		builder.chiudi();
		List<String> nomi = entryNames(destinazione.toByteArray());

		assertEquals(2, builder.getNumeroPdf());
		assertEquals(2, nomi.size());
		assertTrue(nomi.contains("D01_DOC_DOC1.pdf"));
		assertTrue(nomi.contains("D01_AV9.pdf"));
	}

	@Test
	void zipVuotoContieneErroreTxt() throws Exception {
		ByteArrayOutputStream destinazione = new ByteArrayOutputStream();
		ZipStampeBuilder builder = new ZipStampeBuilder(destinazione);
		builder.chiudi();
		List<String> nomi = entryNames(destinazione.toByteArray());
		assertEquals(List.of("errore.txt"), nomi);
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

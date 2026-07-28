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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import it.govpay.tracciati.batch.dto.RisultatoStampa;

/**
 * Costruisce lo ZIP degli avvisi PDF di un tracciato, deduplicando per documento/numero avviso
 * (port di {@code TracciatiUtils.aggiungiStampaAvviso}): per i documenti multi-rata si mantiene una
 * sola copia. Stateful: un'istanza per tracciato.
 */
public class ZipStampeBuilder {

	private static final String MESSAGGIO_NESSUN_AVVISO = "Attenzione: non sono presenti inserimenti andati a buon fine nel tracciato selezionato.";

	private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
	private final ZipOutputStream zos = new ZipOutputStream(this.baos);
	private final Set<String> numeriAvviso = new HashSet<>();
	private final Set<String> numeriDocumento = new HashSet<>();
	private int numeroPdf = 0;

	/** Aggiunge il PDF dell'avviso allo ZIP, saltando gli esiti KO e i duplicati. */
	public void aggiungi(RisultatoStampa risultato) throws IOException {
		if (!risultato.ok() || risultato.pdf() == null) {
			return;
		}
		String dominio = risultato.codDominio() != null ? risultato.codDominio() : "";
		String fileName;
		if (risultato.numeroDocumento() != null) {
			if (!this.numeriDocumento.add(dominio + risultato.numeroDocumento())) {
				return; // documento già inserito (multi-rata)
			}
			fileName = dominio + "_DOC_" + risultato.numeroDocumento() + ".pdf";
		} else {
			if (risultato.numeroAvviso() == null || !this.numeriAvviso.add(dominio + risultato.numeroAvviso())) {
				return; // senza numero avviso, oppure già inserito
			}
			fileName = dominio + "_" + risultato.numeroAvviso() + ".pdf";
		}
		this.zos.putNextEntry(new ZipEntry(fileName));
		this.zos.write(risultato.pdf());
		this.zos.closeEntry();
		this.numeroPdf++;
	}

	/** Numero di PDF effettivamente inseriti (al netto dei duplicati). */
	public int getNumeroPdf() {
		return this.numeroPdf;
	}

	/** Chiude lo ZIP e ne restituisce i byte; se vuoto inserisce un {@code errore.txt} (come il legacy). */
	public byte[] build() throws IOException {
		if (this.numeroPdf == 0) {
			this.zos.putNextEntry(new ZipEntry("errore.txt"));
			this.zos.write(MESSAGGIO_NESSUN_AVVISO.getBytes(StandardCharsets.UTF_8));
			this.zos.closeEntry();
		}
		this.zos.close();
		return this.baos.toByteArray();
	}
}

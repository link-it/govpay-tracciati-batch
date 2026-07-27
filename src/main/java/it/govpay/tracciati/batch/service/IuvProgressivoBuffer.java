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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Component;

/**
 * Buffer in-memory dei progressivi IUV pre-riservati, per ridurre la contesa sul DB quando si
 * riserva un blocco di valori per giro. Port di {@code IDSerialGeneratorBuffer} di GovWay, con la
 * {@code org.openspcoop2.utils.Semaphore} sostituita da un {@link ReentrantLock}.
 *
 * <p>La chiave è l'informazione associata al progressivo (codDominio + prefisso + tipo IUV).</p>
 */
@Component
public class IuvProgressivoBuffer {

	private final ReentrantLock lock = new ReentrantLock();
	private final Map<String, Deque<Long>> buffer = new HashMap<>();

	/** Estrae il prossimo valore bufferizzato per la chiave, o {@code null} se il buffer è vuoto. */
	public Long nextValue(String key) {
		this.lock.lock();
		try {
			Deque<Long> valori = this.buffer.get(key);
			if (valori == null || valori.isEmpty()) {
				return null;
			}
			Long valore = valori.pollFirst();
			if (valori.isEmpty()) {
				this.buffer.remove(key);
			}
			return valore;
		} finally {
			this.lock.unlock();
		}
	}

	/** Accoda i valori residui generati in un giro DB. */
	public void putAll(String key, List<Long> valori) {
		if (valori == null || valori.isEmpty()) {
			return;
		}
		this.lock.lock();
		try {
			this.buffer.computeIfAbsent(key, k -> new ArrayDeque<>()).addAll(valori);
		} finally {
			this.lock.unlock();
		}
	}

	public void clear() {
		this.lock.lock();
		try {
			this.buffer.clear();
		} finally {
			this.lock.unlock();
		}
	}
}

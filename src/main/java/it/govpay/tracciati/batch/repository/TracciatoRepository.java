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
package it.govpay.tracciati.batch.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.tracciati.batch.entity.StatoElaborazione;
import it.govpay.tracciati.batch.entity.Tracciato;
import it.govpay.tracciati.batch.entity.TipoTracciato;

/**
 * Repository dei tracciati di caricamento pendenze.
 */
public interface TracciatoRepository extends JpaRepository<Tracciato, Long> {

	/**
	 * Seleziona i tracciati da elaborare: dato tipo (PENDENZA) e insieme di stati attivi
	 * (ELABORAZIONE, IN_STAMPA), ordinati per id crescente. Replica la selezione della
	 * procedura legacy ({@code Operazioni.elaborazioneTracciatiPendenze}).
	 */
	List<Tracciato> findByTipoAndStatoInOrderByIdAsc(TipoTracciato tipo, Collection<StatoElaborazione> stati, Pageable pageable);
}

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

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.tracciati.batch.entity.Operazione;

/**
 * Repository delle operazioni (esiti per linea) di un tracciato.
 */
public interface OperazioneRepository extends JpaRepository<Operazione, Long> {

	/**
	 * Operazione già registrata per una linea del tracciato. Usata per l'upsert idempotente
	 * in ripartenza (una linea elaborata due volte deve aggiornare, non duplicare).
	 */
	Optional<Operazione> findByIdTracciatoAndLineaElaborazione(Long idTracciato, Long lineaElaborazione);

	/** Numero di operazioni già registrate per il tracciato. */
	long countByIdTracciato(Long idTracciato);

	/** Operazioni del tracciato in ordine di linea, per la produzione dell'esito (paginata). */
	List<Operazione> findByIdTracciatoOrderByLineaElaborazioneAsc(Long idTracciato, Pageable pageable);
}

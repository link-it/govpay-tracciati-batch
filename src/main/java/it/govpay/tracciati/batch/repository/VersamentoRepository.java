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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.govpay.tracciati.batch.entity.Versamento;

/**
 * Repository delle posizioni debitorie (versamenti).
 */
public interface VersamentoRepository extends JpaRepository<Versamento, Long> {

	/** Posizione già presente per (applicazione, cod versamento ente): vincolo di unicità su DB. */
	Optional<Versamento> findByIdApplicazioneAndCodVersamentoEnte(Long idApplicazione, String codVersamentoEnte);

	/**
	 * Posizioni debitorie di un tracciato per cui produrre l'avviso: quelle con numero avviso,
	 * collegate al tracciato tramite le operazioni di caricamento ({@code operazioni.id_versamento}).
	 */
	@Query("SELECT v FROM Versamento v WHERE v.numeroAvviso IS NOT NULL AND v.id IN "
			+ "(SELECT o.idVersamento FROM Operazione o WHERE o.idTracciato = :idTracciato AND o.idVersamento IS NOT NULL)")
	List<Versamento> findVersamentiDaStampare(@Param("idTracciato") Long idTracciato, Pageable pageable);
}

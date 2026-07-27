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
package it.govpay.tracciati.batch.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PDF di stampa (avviso) di una posizione debitoria o di un documento (tabella {@code stampe}).
 */
@Entity
@Table(name = "stampe")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Stampa {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "data_creazione", nullable = false)
	private LocalDateTime dataCreazione;

	@Column(name = "tipo", length = 16, nullable = false)
	private String tipo;

	/** Contenuto PDF (BYTEA). */
	@Column(name = "pdf")
	private byte[] pdf;

	@Column(name = "id_versamento")
	private Long idVersamento;

	@Column(name = "id_documento")
	private Long idDocumento;
}

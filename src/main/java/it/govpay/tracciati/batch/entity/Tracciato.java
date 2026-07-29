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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tracciato di caricamento pendenze (tabella {@code tracciati} dello schema GovPay).
 *
 * <p>Il campo {@code zip_stampe} (Large Object PostgreSQL / BLOB) NON è mappato qui:
 * viene gestito separatamente nello step di stampa (Punto 8), come nella procedura legacy.</p>
 */
@Entity
@Table(name = "tracciati")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tracciato {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "cod_dominio", length = 35, nullable = false)
	private String codDominio;

	@Column(name = "cod_tipo_versamento", length = 35)
	private String codTipoVersamento;

	@Enumerated(EnumType.STRING)
	@Column(name = "formato", length = 10, nullable = false)
	private FormatoTracciato formato;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo", length = 10, nullable = false)
	private TipoTracciato tipo;

	@Enumerated(EnumType.STRING)
	@Column(name = "stato", length = 12, nullable = false)
	private StatoElaborazione stato;

	@Column(name = "descrizione_stato", length = 256)
	private String descrizioneStato;

	@Column(name = "data_caricamento", nullable = false)
	private LocalDateTime dataCaricamento;

	@Column(name = "data_completamento")
	private LocalDateTime dataCompletamento;

	/** JSON serializzato del bean di stato {@code TracciatoPendenza}. */
	@Column(name = "bean_dati")
	private String beanDati;

	@Column(name = "file_name_richiesta", length = 256)
	private String fileNameRichiesta;

	/** Contenuto del file di richiesta (BYTEA). */
	@Column(name = "raw_richiesta")
	private byte[] rawRichiesta;

	@Column(name = "file_name_esito", length = 256)
	private String fileNameEsito;

	/** Contenuto del file di esito (BYTEA). */
	@Column(name = "raw_esito")
	private byte[] rawEsito;

	@Column(name = "id_operatore")
	private Long idOperatore;
}

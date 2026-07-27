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
 * Tipo pendenza personalizzato per un dominio (tabella {@code tipi_vers_domini}).
 *
 * <p>Mappatura in <b>sola lettura</b> del sottoinsieme utile: FK a tipo versamento e dominio,
 * e template tracciato CSV personalizzato per il dominio (prevale sul fallback di sistema).</p>
 */
@Entity
@Table(name = "tipi_vers_domini")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TipoVersamentoDominio {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "id_tipo_versamento", nullable = false)
	private Long idTipoVersamento;

	@Column(name = "id_dominio", nullable = false)
	private Long idDominio;

	@Column(name = "abilitato")
	private Boolean abilitato;

	@Column(name = "codifica_iuv", length = 4)
	private String codificaIuv;

	// Template tracciato CSV personalizzato per dominio
	@Column(name = "trac_csv_tipo", length = 35)
	private String tracCsvTipo;

	@Column(name = "trac_csv_header_risposta")
	private String tracCsvHeaderRisposta;

	@Column(name = "trac_csv_template_richiesta")
	private String tracCsvTemplateRichiesta;

	@Column(name = "trac_csv_template_risposta")
	private String tracCsvTemplateRisposta;
}

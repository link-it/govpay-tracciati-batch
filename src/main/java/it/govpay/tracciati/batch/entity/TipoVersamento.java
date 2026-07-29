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
 * Tipo pendenza a livello di sistema (tabella {@code tipi_versamento}).
 *
 * <p>Mappatura in <b>sola lettura</b> del sottoinsieme utile al caricamento: identificativo,
 * codice e template CSV di sistema (usati come fallback quando il dominio non li personalizza).</p>
 */
@Entity
@Table(name = "tipi_versamento")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TipoVersamento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "cod_tipo_versamento", length = 35, nullable = false)
	private String codTipoVersamento;

	@Column(name = "descrizione", length = 255, nullable = false)
	private String descrizione;

	@Column(name = "codifica_iuv", length = 4)
	private String codificaIuv;

	@Column(name = "abilitato", nullable = false)
	private boolean abilitato;

	// Template tracciato CSV (fallback di sistema)
	@Column(name = "trac_csv_tipo", length = 35)
	private String tracCsvTipo;

	@Column(name = "trac_csv_header_risposta")
	private String tracCsvHeaderRisposta;

	@Column(name = "trac_csv_template_richiesta")
	private String tracCsvTemplateRichiesta;

	@Column(name = "trac_csv_template_risposta")
	private String tracCsvTemplateRisposta;
}

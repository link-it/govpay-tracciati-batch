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
 * Unità operativa di un dominio (tabella {@code uo}). Sull'avviso alimenta il settore dell'ente e
 * le righe di contatto; l'anagrafica del dominio è per convenzione GovPay quella dell'unità
 * operativa {@code EC} (vedi {@code it.govpay.bd.model.Dominio.getAnagrafica}).
 */
@Entity
@Table(name = "uo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitaOperativa {

	/** Codice dell'unità operativa che rappresenta l'ente creditore stesso. */
	public static final String COD_UO_ENTE_CREDITORE = "EC";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "cod_uo", length = 35, nullable = false)
	private String codUo;

	@Column(name = "abilitato", nullable = false)
	private boolean abilitato;

	@Column(name = "uo_denominazione", length = 70)
	private String denominazione;

	@Column(name = "uo_area", length = 255)
	private String area;

	@Column(name = "uo_url_sito_web", length = 255)
	private String urlSitoWeb;

	@Column(name = "uo_email", length = 255)
	private String email;

	@Column(name = "uo_pec", length = 255)
	private String pec;

	@Column(name = "uo_tel", length = 255)
	private String tel;

	@Column(name = "uo_fax", length = 255)
	private String fax;

	@Column(name = "id_dominio", nullable = false)
	private Long idDominio;
}

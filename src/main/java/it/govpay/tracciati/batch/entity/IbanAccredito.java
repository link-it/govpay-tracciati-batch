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
 * Conto di accredito di un dominio (tabella {@code iban_accredito}). Sull'avviso di pagamento serve
 * per il bollettino postale: {@code postale} discrimina i conti di Poste Italiane e
 * {@code autStampaPoste}/{@code intestatario} alimentano autorizzazione e intestazione del conto.
 */
@Entity
@Table(name = "iban_accredito")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IbanAccredito {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "cod_iban", length = 255, nullable = false)
	private String codIban;

	@Column(name = "bic_accredito", length = 255)
	private String bicAccredito;

	@Column(name = "postale", nullable = false)
	private boolean postale;

	@Column(name = "abilitato", nullable = false)
	private boolean abilitato;

	@Column(name = "descrizione", length = 255)
	private String descrizione;

	@Column(name = "intestatario", length = 255)
	private String intestatario;

	@Column(name = "aut_stampa_poste", length = 255)
	private String autStampaPoste;

	@Column(name = "id_dominio", nullable = false)
	private Long idDominio;
}

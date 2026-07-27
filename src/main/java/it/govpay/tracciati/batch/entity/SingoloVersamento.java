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
 * Voce/singolo versamento di una posizione debitoria (tabella {@code singoli_versamenti}).
 */
@Entity
@Table(name = "singoli_versamenti")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SingoloVersamento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "cod_singolo_versamento_ente", length = 70, nullable = false)
	private String codSingoloVersamentoEnte;

	@Column(name = "stato_singolo_versamento", length = 35, nullable = false)
	private String statoSingoloVersamento;

	@Column(name = "importo_singolo_versamento", nullable = false)
	private double importoSingoloVersamento;

	@Column(name = "tipo_bollo", length = 2)
	private String tipoBollo;

	@Column(name = "hash_documento", length = 70)
	private String hashDocumento;

	@Column(name = "provincia_residenza", length = 2)
	private String provinciaResidenza;

	@Column(name = "tipo_contabilita", length = 1)
	private String tipoContabilita;

	@Column(name = "codice_contabilita", length = 255)
	private String codiceContabilita;

	@Column(name = "descrizione", length = 256)
	private String descrizione;

	@Column(name = "dati_allegati")
	private String datiAllegati;

	@Column(name = "indice_dati", nullable = false)
	private int indiceDati;

	@Column(name = "descrizione_causale_rpt", length = 140)
	private String descrizioneCausaleRpt;

	@Column(name = "contabilita")
	private String contabilita;

	@Column(name = "metadata")
	private String metadata;

	@Column(name = "id_versamento", nullable = false)
	private Long idVersamento;

	@Column(name = "id_tributo")
	private Long idTributo;

	@Column(name = "id_iban_accredito")
	private Long idIbanAccredito;

	@Column(name = "id_iban_appoggio")
	private Long idIbanAppoggio;

	@Column(name = "id_dominio")
	private Long idDominio;
}

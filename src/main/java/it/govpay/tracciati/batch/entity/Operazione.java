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
 * Esito dell'elaborazione di una singola linea del tracciato (tabella {@code operazioni}).
 * Univoca per {@code (id_tracciato, linea_elaborazione)} ai fini dell'idempotenza/upsert.
 */
@Entity
@Table(name = "operazioni")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Operazione {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "id_tracciato", nullable = false)
	private Long idTracciato;

	@Column(name = "linea_elaborazione", nullable = false)
	private Long lineaElaborazione;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo_operazione", length = 16, nullable = false)
	private TipoOperazione tipoOperazione;

	@Enumerated(EnumType.STRING)
	@Column(name = "stato", length = 16, nullable = false)
	private StatoOperazione stato;

	@Column(name = "dati_richiesta")
	private byte[] datiRichiesta;

	@Column(name = "dati_risposta")
	private byte[] datiRisposta;

	@Column(name = "dettaglio_esito", length = 255)
	private String dettaglioEsito;

	@Column(name = "cod_versamento_ente", length = 255)
	private String codVersamentoEnte;

	@Column(name = "cod_dominio", length = 35)
	private String codDominio;

	@Column(name = "iuv", length = 35)
	private String iuv;

	@Column(name = "trn", length = 35)
	private String trn;

	@Column(name = "id_applicazione")
	private Long idApplicazione;

	@Column(name = "id_stampa")
	private Long idStampa;

	@Column(name = "id_versamento")
	private Long idVersamento;
}

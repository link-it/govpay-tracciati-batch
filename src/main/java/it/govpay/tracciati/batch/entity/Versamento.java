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
 * Posizione debitoria (tabella {@code versamenti} dello schema GovPay).
 *
 * <p>Le foreign key sono mappate come semplici colonne {@code Long} (id_*) per semplificare
 * l'insert nello step di caricamento; le anagrafiche (dominio/applicazione/tipo versamento)
 * sono risolte tramite i rispettivi repository.</p>
 */
@Entity
@Table(name = "versamenti")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Versamento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "cod_versamento_ente", length = 35, nullable = false)
	private String codVersamentoEnte;

	@Column(name = "nome", length = 35)
	private String nome;

	@Column(name = "importo_totale", nullable = false)
	private double importoTotale;

	@Column(name = "stato_versamento", length = 35, nullable = false)
	private String statoVersamento;

	@Column(name = "descrizione_stato", length = 255)
	private String descrizioneStato;

	@Column(name = "aggiornabile", nullable = false)
	private boolean aggiornabile;

	@Column(name = "data_creazione", nullable = false)
	private LocalDateTime dataCreazione;

	@Column(name = "data_validita")
	private LocalDateTime dataValidita;

	@Column(name = "data_scadenza")
	private LocalDateTime dataScadenza;

	@Column(name = "data_ora_ultimo_aggiornamento", nullable = false)
	private LocalDateTime dataOraUltimoAggiornamento;

	@Column(name = "causale_versamento", length = 1024)
	private String causaleVersamento;

	@Column(name = "debitore_tipo", length = 1)
	private String debitoreTipo;

	@Column(name = "debitore_identificativo", length = 35, nullable = false)
	private String debitoreIdentificativo;

	@Column(name = "debitore_anagrafica", length = 70, nullable = false)
	private String debitoreAnagrafica;

	@Column(name = "debitore_indirizzo", length = 70)
	private String debitoreIndirizzo;

	@Column(name = "debitore_civico", length = 16)
	private String debitoreCivico;

	@Column(name = "debitore_cap", length = 16)
	private String debitoreCap;

	@Column(name = "debitore_localita", length = 35)
	private String debitoreLocalita;

	@Column(name = "debitore_provincia", length = 35)
	private String debitoreProvincia;

	@Column(name = "debitore_nazione", length = 2)
	private String debitoreNazione;

	@Column(name = "debitore_email", length = 256)
	private String debitoreEmail;

	@Column(name = "debitore_telefono", length = 35)
	private String debitoreTelefono;

	@Column(name = "debitore_cellulare", length = 35)
	private String debitoreCellulare;

	@Column(name = "debitore_fax", length = 35)
	private String debitoreFax;

	@Column(name = "tassonomia_avviso", length = 35)
	private String tassonomiaAvviso;

	@Column(name = "tassonomia", length = 35)
	private String tassonomia;

	@Column(name = "cod_lotto", length = 35)
	private String codLotto;

	@Column(name = "cod_versamento_lotto", length = 35)
	private String codVersamentoLotto;

	@Column(name = "cod_anno_tributario", length = 35)
	private String codAnnoTributario;

	@Column(name = "cod_bundlekey", length = 256)
	private String codBundlekey;

	@Column(name = "dati_allegati")
	private String datiAllegati;

	@Column(name = "incasso", length = 1)
	private String incasso;

	@Column(name = "anomalie")
	private String anomalie;

	@Column(name = "iuv_versamento", length = 35)
	private String iuvVersamento;

	@Column(name = "numero_avviso", length = 35)
	private String numeroAvviso;

	@Column(name = "ack", nullable = false)
	private boolean ack;

	@Column(name = "anomalo", nullable = false)
	private boolean anomalo;

	@Column(name = "divisione", length = 35)
	private String divisione;

	@Column(name = "direzione", length = 35)
	private String direzione;

	@Column(name = "id_sessione", length = 35)
	private String idSessione;

	@Column(name = "data_pagamento")
	private LocalDateTime dataPagamento;

	@Column(name = "importo_pagato", nullable = false)
	private double importoPagato;

	@Column(name = "importo_incassato", nullable = false)
	private double importoIncassato;

	@Column(name = "stato_pagamento", length = 35, nullable = false)
	private String statoPagamento;

	@Column(name = "iuv_pagamento", length = 35)
	private String iuvPagamento;

	@Column(name = "src_iuv", length = 35)
	private String srcIuv;

	@Column(name = "src_debitore_identificativo", length = 35, nullable = false)
	private String srcDebitoreIdentificativo;

	@Column(name = "cod_rata", length = 35)
	private String codRata;

	@Column(name = "tipo", length = 35, nullable = false)
	private String tipo;

	@Column(name = "data_notifica_avviso")
	private LocalDateTime dataNotificaAvviso;

	@Column(name = "avviso_notificato")
	private Boolean avvisoNotificato;

	@Column(name = "avv_mail_data_prom_scadenza")
	private LocalDateTime avvMailDataPromScadenza;

	@Column(name = "avv_mail_prom_scad_notificato")
	private Boolean avvMailPromScadNotificato;

	@Column(name = "avv_app_io_data_prom_scadenza")
	private LocalDateTime avvAppIoDataPromScadenza;

	@Column(name = "avv_app_io_prom_scad_notificat")
	private Boolean avvAppIoPromScadNotificat;

	@Column(name = "proprieta")
	private String proprieta;

	@Column(name = "data_ultima_modifica_aca")
	private LocalDateTime dataUltimaModificaAca;

	@Column(name = "data_ultima_comunicazione_aca")
	private LocalDateTime dataUltimaComunicazioneAca;

	// ── Foreign key (mappate come colonne id) ──────────────────────────────
	@Column(name = "id_tipo_versamento_dominio", nullable = false)
	private Long idTipoVersamentoDominio;

	@Column(name = "id_tipo_versamento", nullable = false)
	private Long idTipoVersamento;

	@Column(name = "id_dominio", nullable = false)
	private Long idDominio;

	@Column(name = "id_uo")
	private Long idUo;

	@Column(name = "id_applicazione", nullable = false)
	private Long idApplicazione;

	@Column(name = "id_documento")
	private Long idDocumento;
}

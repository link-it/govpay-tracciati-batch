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
package it.govpay.tracciati.batch.dto;

/**
 * Template CSV di risposta risolto per il tracciato: intestazione (testo) e template FreeMarker
 * (Base64) da applicare a ciascuna operazione. Risolti da tipo versamento (dominio) o configurazione
 * di sistema in fase di assemblaggio dello step.
 */
public record EsitoCsvTemplate(String headerRisposta, String templateRispostaBase64) {
}

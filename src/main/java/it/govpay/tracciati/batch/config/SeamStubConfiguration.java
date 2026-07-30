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
package it.govpay.tracciati.batch.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import it.govpay.tracciati.batch.dto.AnnullamentoPendenza;
import it.govpay.tracciati.batch.dto.EsitoCaricamento;
import it.govpay.tracciati.batch.dto.PendenzaPost;
import it.govpay.tracciati.batch.entity.Tracciato;
import it.govpay.tracciati.batch.service.CaricamentoService;

/**
 * Implementazione STUB del seam ancora aperto {@link CaricamentoService}, fornita solo per
 * consentire il wiring del contesto e l'assemblaggio del Job in assenza di un DB GovPay reale.
 *
 * <p>Attiva solo se non esiste già un bean concreto ({@code @ConditionalOnMissingBean}): quando verrà
 * implementato il servizio reale (risoluzione anagrafica + IUV + insert) questo stub verrà
 * automaticamente sostituito. Ritorna sempre esito KO.</p>
 */
@Configuration
public class SeamStubConfiguration {

	@Bean
	@ConditionalOnMissingBean(CaricamentoService.class)
	public CaricamentoService caricamentoServiceStub() {
		return new CaricamentoService() {
			@Override
			public EsitoCaricamento caricaPendenza(PendenzaPost pendenza, Tracciato tracciato) {
				return EsitoCaricamento.ko("CaricamentoService non implementato (stub)");
			}

			@Override
			public EsitoCaricamento annullaPendenza(AnnullamentoPendenza annullamento, Tracciato tracciato) {
				return EsitoCaricamento.ko("CaricamentoService non implementato (stub)");
			}
		};
	}
}

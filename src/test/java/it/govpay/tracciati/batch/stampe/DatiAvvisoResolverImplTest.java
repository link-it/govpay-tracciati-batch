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
package it.govpay.tracciati.batch.stampe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import it.govpay.common.entity.DominioEntity;
import it.govpay.common.repository.DominioRepository;
import it.govpay.tracciati.batch.entity.Versamento;
import it.govpay.tracciati.batch.repository.DominioLogoBatchRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Valorizzazione di {@code creditor.pay_on_website} nel payload verso govpay-stampe-api (issue #20).
 */
class DatiAvvisoResolverImplTest {

	private static final long ID_DOMINIO = 1L;

	private final DominioRepository dominioRepository = mock(DominioRepository.class);
	private final DominioLogoBatchRepository dominioLogoRepository = mock(DominioLogoBatchRepository.class);
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	DatiAvvisoResolverImplTest() {
		DominioEntity dominio = new DominioEntity();
		dominio.setId(ID_DOMINIO);
		dominio.setCodDominio("01234567890");
		dominio.setRagioneSociale("Comune di Test");
		when(this.dominioRepository.findById(ID_DOMINIO)).thenReturn(Optional.of(dominio));
		when(this.dominioLogoRepository.findByCodDominio("01234567890")).thenReturn(Optional.empty());
	}

	@Test
	void senzaDatoDelDominioPayOnWebsiteFalse() {
		DatiAvvisoCreditore dati = resolver(false).risolvi(versamento());

		assertEquals(Boolean.FALSE, dati.creditor().getPayOnWebsite());
		assertTrue(this.jsonMapper.writeValueAsString(dati.creditor()).contains("\"pay_on_website\":false"));
	}

	@Test
	void enteCheConsenteIlPagamentoSulSitoPayOnWebsiteTrue() {
		DatiAvvisoCreditore dati = resolver(true).risolvi(versamento());

		assertEquals(Boolean.TRUE, dati.creditor().getPayOnWebsite());
		assertTrue(this.jsonMapper.writeValueAsString(dati.creditor()).contains("\"pay_on_website\":true"));
	}

	private DatiAvvisoResolverImpl resolver(boolean sitoEnte) {
		return new DatiAvvisoResolverImpl(this.dominioRepository, this.dominioLogoRepository,
				new DefaultResourceLoader(), "classpath:stampe/logo-default.png") {
			@Override
			boolean consentePagamentoSulSito(DominioEntity dominio) {
				return sitoEnte;
			}
		};
	}

	private static Versamento versamento() {
		return Versamento.builder()
				.idDominio(ID_DOMINIO)
				.numeroAvviso("301000000000000123")
				.importoTotale(10.0)
				.build();
	}
}

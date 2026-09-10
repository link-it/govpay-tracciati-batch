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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import it.govpay.common.entity.DominioEntity;
import it.govpay.common.entity.DominioLogoEntity;
import it.govpay.common.repository.DominioLogoRepository;
import it.govpay.common.repository.DominioRepository;
import it.govpay.tracciati.batch.entity.SingoloVersamento;
import it.govpay.tracciati.batch.entity.UnitaOperativa;
import it.govpay.tracciati.batch.entity.Versamento;
import it.govpay.tracciati.batch.repository.SingoloVersamentoRepository;
import it.govpay.tracciati.batch.repository.UnitaOperativaRepository;

/** Anagrafica dell'ente creditore sull'avviso: contatti, logo del dominio e logo di default. */
class DatiCreditoreResolverImplTest {

	private static final String LOGO_DEFAULT = "data:image/png;base64,AAAA";

	private final DominioRepository dominioRepository = mock(DominioRepository.class);
	private final DominioLogoRepository dominioLogoRepository = mock(DominioLogoRepository.class);
	private final UnitaOperativaRepository unitaOperativaRepository = mock(UnitaOperativaRepository.class);
	private final SingoloVersamentoRepository singoloVersamentoRepository = mock(SingoloVersamentoRepository.class);

	private final DatiCreditoreResolverImpl resolver = new DatiCreditoreResolverImpl(this.dominioRepository,
			this.dominioLogoRepository, this.unitaOperativaRepository, this.singoloVersamentoRepository, LOGO_DEFAULT);

	private final Versamento versamento = Versamento.builder().id(1L).idDominio(2L).build();

	private void dominio() {
		DominioEntity dominio = new DominioEntity();
		dominio.setId(2L);
		dominio.setCodDominio("01234567890");
		dominio.setRagioneSociale("Comune di Test");
		dominio.setCbill("ABCDE");
		dominio.setAutStampaPoste("Aut. Poste 123");
		when(this.dominioRepository.findById(2L)).thenReturn(Optional.of(dominio));
	}

	private void unitaOperativaEnteCreditore(UnitaOperativa uo) {
		when(this.unitaOperativaRepository.findByIdDominioAndCodUo(2L, UnitaOperativa.COD_UO_ENTE_CREDITORE))
				.thenReturn(Optional.ofNullable(uo));
	}

	@Test
	void anagraficaDalDominioEDallUnitaOperativaEnteCreditore() {
		dominio();
		unitaOperativaEnteCreditore(UnitaOperativa.builder().id(9L).codUo("EC").idDominio(2L)
				.area("Ufficio Tributi").urlSitoWeb("https://comune.test.it")
				.tel("06 000000").fax("06 111111").pec("pec@comune.test.it").build());
		when(this.dominioLogoRepository.findById(2L)).thenReturn(Optional.empty());
		when(this.singoloVersamentoRepository.findByIdVersamento(1L)).thenReturn(List.of());

		DatiCreditore dati = this.resolver.risolvi(this.versamento);

		assertEquals("01234567890", dati.creditor().getFiscalCode());
		assertEquals("Comune di Test", dati.creditor().getBusinessName());
		assertEquals("ABCDE", dati.creditor().getCbillCode());
		assertEquals("Aut. Poste 123", dati.creditor().getPostalAuthMessage());
		assertEquals("Ufficio Tributi", dati.creditor().getDepartmentName());
		// il contratto ha due righe di contatto: si inviano le prime due dell'ordine legacy
		assertEquals("https://comune.test.it", dati.creditor().getInfoLine1());
		assertEquals("Tel: 06 000000 - Fax: 06 111111", dati.creditor().getInfoLine2());
	}

	@Test
	void senzaLogoDelDominioSiUsaIlLogoDiDefault() {
		dominio();
		unitaOperativaEnteCreditore(null);
		when(this.dominioLogoRepository.findById(2L)).thenReturn(Optional.empty());
		when(this.singoloVersamentoRepository.findByIdVersamento(1L)).thenReturn(List.of());

		DatiCreditore dati = this.resolver.risolvi(this.versamento);

		assertArrayEquals(LOGO_DEFAULT.getBytes(StandardCharsets.UTF_8), dati.logo());
		assertNull(dati.logoSecondario());
	}

	@Test
	void logoDelDominioQuandoPresente() {
		dominio();
		unitaOperativaEnteCreditore(null);
		DominioLogoEntity logo = new DominioLogoEntity();
		logo.setLogo("data:image/png;base64,DOMINIO".getBytes(StandardCharsets.UTF_8));
		when(this.dominioLogoRepository.findById(2L)).thenReturn(Optional.of(logo));
		when(this.singoloVersamentoRepository.findByIdVersamento(1L)).thenReturn(List.of());

		assertArrayEquals("data:image/png;base64,DOMINIO".getBytes(StandardCharsets.UTF_8),
				this.resolver.risolvi(this.versamento).logo());
	}

	@Test
	void pendenzaMultibeneficiarioAggiungeIlLogoSecondario() {
		dominio();
		unitaOperativaEnteCreditore(null);
		when(this.dominioLogoRepository.findById(2L)).thenReturn(Optional.empty());
		when(this.singoloVersamentoRepository.findByIdVersamento(1L)).thenReturn(List.of(
				SingoloVersamento.builder().id(10L).indiceDati(1).idDominio(2L).build(),
				SingoloVersamento.builder().id(11L).indiceDati(2).idDominio(3L).build()));
		DominioLogoEntity logoSecondario = new DominioLogoEntity();
		logoSecondario.setLogo("data:image/png;base64,SECONDO".getBytes(StandardCharsets.UTF_8));
		when(this.dominioLogoRepository.findById(3L)).thenReturn(Optional.of(logoSecondario));

		DatiCreditore dati = this.resolver.risolvi(this.versamento);

		assertArrayEquals("data:image/png;base64,SECONDO".getBytes(StandardCharsets.UTF_8), dati.logoSecondario());
	}
}

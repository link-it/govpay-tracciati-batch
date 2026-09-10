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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import it.govpay.tracciati.batch.entity.IbanAccredito;
import it.govpay.tracciati.batch.entity.SingoloVersamento;
import it.govpay.tracciati.batch.entity.Versamento;
import it.govpay.tracciati.batch.repository.IbanAccreditoRepository;
import it.govpay.tracciati.batch.repository.SingoloVersamentoRepository;
import it.govpay.tracciati.stampe.client.model.Iban;

/**
 * Scelta dell'IBAN dell'avviso: conto di accredito se postale, altrimenti conto di appoggio se
 * postale, guardando la prima voce della posizione.
 */
class IbanAvvisoResolverTest {

	private final SingoloVersamentoRepository singoloVersamentoRepository = mock(SingoloVersamentoRepository.class);
	private final IbanAccreditoRepository ibanAccreditoRepository = mock(IbanAccreditoRepository.class);
	private final IbanAvvisoResolver resolver = new IbanAvvisoResolver(this.singoloVersamentoRepository,
			this.ibanAccreditoRepository);

	private final Versamento versamento = Versamento.builder().id(1L).build();

	private IbanAccredito iban(long id, boolean postale) {
		return IbanAccredito.builder().id(id).codIban("IT60X054281110100000012345" + id).postale(postale)
				.intestatario("Comune di Test").autStampaPoste("Aut. 123").build();
	}

	private void voce(Long idAccredito, Long idAppoggio) {
		when(this.singoloVersamentoRepository.findByIdVersamento(1L)).thenReturn(List.of(
				SingoloVersamento.builder().id(10L).indiceDati(1).idIbanAccredito(idAccredito).idIbanAppoggio(idAppoggio).build()));
	}

	@Test
	void ibanDiAccreditoPostale() {
		voce(5L, 6L);
		when(this.ibanAccreditoRepository.findById(5L)).thenReturn(Optional.of(iban(5, true)));

		Optional<Iban> risolto = this.resolver.ibanPostale(this.versamento);

		assertTrue(risolto.isPresent());
		assertEquals("IT60X0542811101000000123455", risolto.get().getIbanCode());
		assertEquals("Comune di Test", risolto.get().getOwnerBusinessName());
		assertEquals("Aut. 123", risolto.get().getPostalAuthMessage());
	}

	@Test
	void fallbackSulContoDiAppoggioQuandoAccreditoNonEPostale() {
		voce(5L, 6L);
		when(this.ibanAccreditoRepository.findById(5L)).thenReturn(Optional.of(iban(5, false)));
		when(this.ibanAccreditoRepository.findById(6L)).thenReturn(Optional.of(iban(6, true)));

		assertEquals("IT60X0542811101000000123456", this.resolver.ibanPostale(this.versamento).orElseThrow().getIbanCode());
	}

	@Test
	void nessunContoPostaleNessunIban() {
		voce(5L, null);
		when(this.ibanAccreditoRepository.findById(anyLong())).thenReturn(Optional.of(iban(5, false)));

		assertTrue(this.resolver.ibanPostale(this.versamento).isEmpty());
	}

	@Test
	void posizioneSenzaVociNessunIban() {
		when(this.singoloVersamentoRepository.findByIdVersamento(1L)).thenReturn(List.of());
		assertTrue(this.resolver.ibanPostale(this.versamento).isEmpty());
	}
}

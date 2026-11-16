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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import it.govpay.common.batch.config.ClusterIdPredefinito;
import it.govpay.common.batch.metrics.BatchMetricsNodo;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.tracciati.batch.GovpayTracciatiBatchApplication;
import it.govpay.tracciati.batch.gde.GdeService;

/**
 * Verifica che il cluster-id del nodo sia lo stesso per il {@link JobExecutionHelper},
 * per gli eventi del GDE e per il tag {@code nodo} delle metriche (govpay-common#35, #36).
 */
class ClusterIdConfigTest {

	@Nested
	@SpringBootTest(classes = GovpayTracciatiBatchApplication.class)
	@ActiveProfiles("test")
	class SenzaProperty {

		@Autowired
		private JobExecutionHelper jobExecutionHelper;

		@Autowired
		private GdeService gdeService;

		@Autowired
		private BatchMetricsNodo batchMetricsNodo;

		@Test
		void usaIlClusterIdPredefinitoDelNodo() {
			String atteso = ClusterIdPredefinito.valore();

			assertThat(jobExecutionHelper.getClusterId()).isEqualTo(atteso);
			assertThat(ReflectionTestUtils.getField(gdeService, "clusterId")).isEqualTo(atteso);
			assertThat(batchMetricsNodo.valore()).isEqualTo(atteso);
		}
	}

	@Nested
	@SpringBootTest(classes = GovpayTracciatiBatchApplication.class)
	@ActiveProfiles("test")
	@TestPropertySource(properties = "govpay.batch.cluster-id=nodo-1")
	class ConProperty {

		@Autowired
		private JobExecutionHelper jobExecutionHelper;

		@Autowired
		private GdeService gdeService;

		@Autowired
		private BatchMetricsNodo batchMetricsNodo;

		@Test
		void usaIlClusterIdConfigurato() {
			assertThat(jobExecutionHelper.getClusterId()).isEqualTo("nodo-1");
			assertThat(ReflectionTestUtils.getField(gdeService, "clusterId")).isEqualTo("nodo-1");
			assertThat(batchMetricsNodo.valore()).isEqualTo("nodo-1");
			assertThat(batchMetricsNodo.fonte()).isEqualTo(BatchMetricsNodo.PROPERTY_CLUSTER_ID);
		}
	}
}

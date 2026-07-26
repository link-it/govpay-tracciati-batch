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
package it.govpay.tracciati.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Applicazione batch GovPay per l'elaborazione dei tracciati di caricamento pendenze.
 *
 * <p>Riusa il framework batch di {@code govpay-common} ({@code it.govpay.common.batch.*},
 * {@code it.govpay.common.client.*}) per orchestrazione multi-nodo, client HTTP e configurazione.
 * Le entity/repository del dominio pendenze/tracciati sono definite localmente in questo modulo.</p>
 */
@SpringBootApplication(scanBasePackages = {"it.govpay.tracciati.batch", "it.govpay.common.client"})
@EntityScan(basePackages = {"it.govpay.tracciati.batch", "it.govpay.common.client", "it.govpay.common.entity"})
@EnableJpaRepositories(basePackages = {"it.govpay.tracciati.batch"})
@EnableScheduling
public class GovpayTracciatiBatchApplication {

	public static void main(String[] args) {
		SpringApplication.run(GovpayTracciatiBatchApplication.class, args);
	}
}

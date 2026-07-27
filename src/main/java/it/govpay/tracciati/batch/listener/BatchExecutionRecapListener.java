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
package it.govpay.tracciati.batch.listener;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

import it.govpay.common.batch.listener.AbstractBatchExecutionListener;

/**
 * Listener di riepilogo dell'esecuzione del job tracciati (log inizio/fine + statistiche step),
 * basato sul framework di govpay-common.
 */
@Component
public class BatchExecutionRecapListener extends AbstractBatchExecutionListener {

	@Override
	protected String getBatchName() {
		return "Elaborazione Tracciati Pendenze";
	}

	@Override
	protected void printStepStatistics(JobExecution jobExecution) {
		int stepNumber = 1;
		for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
			printSimpleStepStats(stepExecution, stepNumber++, stepExecution.getStepName());
		}
	}
}

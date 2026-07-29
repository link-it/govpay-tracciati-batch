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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;

import it.govpay.tracciati.batch.Costanti;
import it.govpay.tracciati.batch.dto.RigaTracciato;
import it.govpay.tracciati.batch.dto.RisultatoStampa;
import it.govpay.tracciati.batch.dto.TracciatoPendenza;
import it.govpay.tracciati.batch.entity.FormatoTracciato;
import it.govpay.tracciati.batch.entity.StatoElaborazione;
import it.govpay.tracciati.batch.entity.Tracciato;
import it.govpay.tracciati.batch.entity.Versamento;
import it.govpay.tracciati.batch.listener.BatchExecutionRecapListener;
import it.govpay.tracciati.batch.repository.OperazioneRepository;
import it.govpay.tracciati.batch.repository.TracciatoRepository;
import it.govpay.tracciati.batch.repository.VersamentoRepository;
import it.govpay.tracciati.batch.service.CaricamentoService;
import it.govpay.tracciati.batch.service.FinalizzazioneTracciatoService;
import it.govpay.tracciati.batch.service.ProduzioneEsitoService;
import it.govpay.tracciati.batch.stampe.StampaAvvisoService;
import it.govpay.tracciati.batch.stampe.ZipStampeBuilder;
import it.govpay.tracciati.batch.step.caricamento.CaricamentoItemProcessor;
import it.govpay.tracciati.batch.step.caricamento.CaricamentoItemWriter;
import it.govpay.tracciati.batch.step.caricamento.JsonTracciatoItemReader;
import tools.jackson.databind.ObjectMapper;

/**
 * Configurazione del job Spring Batch di elaborazione dei tracciati di caricamento pendenze,
 * con gli step reali. Un'esecuzione elabora un tracciato (individuato dal contesto
 * {@link ElaborazioneTracciatoContext}) attraversando le fasi con guardie di stato che ne
 * consentono anche la ripresa (COMPLETATO / IN_STAMPA):
 * <ol>
 *   <li>{@code caricamentoPendenzeStep} — caricamento in lotti paralleli, solo se stato ELABORAZIONE;</li>
 *   <li>{@code produzioneEsitoStep} — genera l'esito;</li>
 *   <li>{@code completaCaricamentoStep} — CARICAMENTO_OK/KO e transizione IN_STAMPA/COMPLETATO;</li>
 *   <li>{@code stampaAvvisiStep} — produzione avvisi in lotti paralleli, solo se IN_STAMPA e stampaAvvisi;</li>
 *   <li>{@code finalizzazioneStep} — COMPLETATO al termine della stampa.</li>
 * </ol>
 *
 * <p>Caricamento e stampa parallelizzano per lotti su pool dedicati, con dimensione lotto e pool
 * configurabili da properties ({@code govpay.batch.caricamento.*} / {@code govpay.batch.stampe.*}),
 * con la stessa semantica della procedura legacy.</p>
 *
 * <p>Nota: la persistenza dello ZIP su {@code zip_stampe} (OID/BLOB) è il prossimo affinamento.</p>
 */
@Configuration
public class BatchJobConfiguration {

	private static final Logger log = LoggerFactory.getLogger(BatchJobConfiguration.class);
	private static final int PAGE_SIZE_STAMPE = 500;

	private final JobRepository jobRepository;
	private final PlatformTransactionManager transactionManager;
	private final BatchProperties batchProperties;

	public BatchJobConfiguration(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			BatchProperties batchProperties) {
		this.jobRepository = jobRepository;
		this.transactionManager = transactionManager;
		this.batchProperties = batchProperties;
	}

	@Bean(name = Costanti.ELABORAZIONE_TRACCIATI_PENDENZE_JOB_NAME)
	public Job elaborazioneTracciatiPendenzeJob(
			Step caricamentoPendenzeStep,
			Step produzioneEsitoStep,
			Step completaCaricamentoStep,
			Step stampaAvvisiStep,
			Step finalizzazioneStep,
			BatchExecutionRecapListener batchExecutionRecapListener) {
		return new JobBuilder(Costanti.ELABORAZIONE_TRACCIATI_PENDENZE_JOB_NAME, this.jobRepository)
				.incrementer(new RunIdIncrementer())
				.listener(batchExecutionRecapListener)
				.start(caricamentoPendenzeStep)
				.next(produzioneEsitoStep)
				.next(completaCaricamentoStep)
				.next(stampaAvvisiStep)
				.next(finalizzazioneStep)
				.build();
	}

	// ── Step 1: caricamento pendenze (tasklet con lotti paralleli) ───────────

	@Bean
	public Step caricamentoPendenzeStep(ElaborazioneTracciatoContext context, ObjectMapper objectMapper,
			CaricamentoItemProcessor caricamentoItemProcessor, CaricamentoService caricamentoService,
			OperazioneRepository operazioneRepository, SimpleAsyncTaskExecutor caricamentoTaskExecutor) {
		Tasklet tasklet = (contribution, chunkContext) -> {
			if (context.isPresente() && context.getTracciato().getStato() == StatoElaborazione.ELABORAZIONE) {
				eseguiCaricamento(context, objectMapper, caricamentoItemProcessor, caricamentoService,
						operazioneRepository, caricamentoTaskExecutor);
			}
			return RepeatStatus.FINISHED;
		};
		return new StepBuilder("caricamentoPendenzeStep", this.jobRepository)
				.tasklet(tasklet, this.transactionManager).build();
	}

	/**
	 * Caricamento delle pendenze del tracciato in lotti paralleli (dimensione lotto e pool da
	 * properties), come i {@code CaricamentoTracciatoThread} della procedura legacy. Reader e writer
	 * sono costruiti sul thread principale (accesso al contesto {@code @JobScope}); i lotti vengono
	 * processati sui thread del pool usando solo componenti singleton, evitando l'accesso agli scope
	 * job/step dai thread worker.
	 */
	private void eseguiCaricamento(ElaborazioneTracciatoContext context, ObjectMapper objectMapper,
			CaricamentoItemProcessor processor, CaricamentoService caricamentoService,
			OperazioneRepository operazioneRepository, SimpleAsyncTaskExecutor executor) {
		Tracciato tracciato = context.getTracciato();
		TracciatoPendenza beanDati = context.getBeanDati();
		if (tracciato.getFormato() == FormatoTracciato.CSV) {
			// TODO(anagrafica): risoluzione del template CSV di richiesta dal tipo versamento/dominio (seam)
			throw new UnsupportedOperationException(
					"Risoluzione del template CSV di richiesta non ancora implementata (dipende dall'anagrafica dominio/tipo versamento)");
		}

		JsonTracciatoItemReader reader = new JsonTracciatoItemReader(objectMapper);
		reader.bind(tracciato, beanDati);
		CaricamentoItemWriter writer = new CaricamentoItemWriter(caricamentoService, operazioneRepository);
		writer.bind(tracciato, beanDati);

		List<RigaTracciato> righe = new ArrayList<>();
		RigaTracciato riga;
		while ((riga = reader.read()) != null) {
			righe.add(riga);
		}

		int lotto = this.batchProperties.getCaricamentoChunkSize();
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (int i = 0; i < righe.size(); i += lotto) {
			List<RigaTracciato> sottoInsieme = List.copyOf(righe.subList(i, Math.min(i + lotto, righe.size())));
			futures.add(CompletableFuture.runAsync(() -> elaboraLotto(processor, writer, sottoInsieme), executor));
		}
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
	}

	private void elaboraLotto(CaricamentoItemProcessor processor, CaricamentoItemWriter writer, List<RigaTracciato> righe) {
		try {
			List<RigaTracciato> elaborate = new ArrayList<>(righe.size());
			for (RigaTracciato riga : righe) {
				elaborate.add(processor.process(riga));
			}
			writer.write(new org.springframework.batch.infrastructure.item.Chunk<>(elaborate));
		} catch (Exception e) {
			throw new java.util.concurrent.CompletionException(e);
		}
	}

	// ── Step 2: produzione esito ────────────────────────────────────────────

	@Bean
	public Step produzioneEsitoStep(ElaborazioneTracciatoContext context, ProduzioneEsitoService produzioneEsitoService,
			TracciatoRepository tracciatoRepository) {
		Tasklet tasklet = (contribution, chunkContext) -> {
			if (context.isPresente() && context.getTracciato().getStato() == StatoElaborazione.ELABORAZIONE) {
				Tracciato tracciato = context.getTracciato();
				// TODO(anagrafica): per i tracciati CSV passare il template di risposta risolto
				produzioneEsitoService.produciEsito(tracciato, null);
				tracciatoRepository.save(tracciato);
			}
			return RepeatStatus.FINISHED;
		};
		return new StepBuilder("produzioneEsitoStep", this.jobRepository)
				.tasklet(tasklet, this.transactionManager).build();
	}

	// ── Step 3: chiusura caricamento (stato dettaglio + IN_STAMPA/COMPLETATO) ─

	@Bean
	public Step completaCaricamentoStep(ElaborazioneTracciatoContext context,
			FinalizzazioneTracciatoService finalizzazioneService) {
		Tasklet tasklet = (contribution, chunkContext) -> {
			if (context.isPresente() && context.getTracciato().getStato() == StatoElaborazione.ELABORAZIONE) {
				finalizzazioneService.completaCaricamento(context.getTracciato(), context.getBeanDati());
			}
			return RepeatStatus.FINISHED;
		};
		return new StepBuilder("completaCaricamentoStep", this.jobRepository)
				.tasklet(tasklet, this.transactionManager).build();
	}

	// ── Step 4: stampa avvisi (solo IN_STAMPA + stampaAvvisi) ────────────────

	@Bean
	public Step stampaAvvisiStep(ElaborazioneTracciatoContext context, StampaAvvisoService stampaAvvisoService,
			VersamentoRepository versamentoRepository, SimpleAsyncTaskExecutor stampeTaskExecutor) {
		Tasklet tasklet = (contribution, chunkContext) -> {
			if (context.isPresente() && context.getTracciato().getStato() == StatoElaborazione.IN_STAMPA
					&& context.getBeanDati().isStampaAvvisi()) {
				eseguiStampe(context, stampaAvvisoService, versamentoRepository, stampeTaskExecutor);
			}
			return RepeatStatus.FINISHED;
		};
		return new StepBuilder("stampaAvvisiStep", this.jobRepository)
				.tasklet(tasklet, this.transactionManager).build();
	}

	/**
	 * Produce gli avvisi in parallelo (lotti da {@code stampeChunkSize} sul pool stampe) e li aggiunge
	 * allo ZIP in modo sequenziale (lo ZIP non è thread-safe), come nella procedura legacy.
	 */
	private void eseguiStampe(ElaborazioneTracciatoContext context, StampaAvvisoService stampaAvvisoService,
			VersamentoRepository versamentoRepository, SimpleAsyncTaskExecutor stampeTaskExecutor) throws Exception {
		Tracciato tracciato = context.getTracciato();
		TracciatoPendenza beanDati = context.getBeanDati();
		ZipStampeBuilder zipBuilder = new ZipStampeBuilder();
		int lotto = this.batchProperties.getStampeChunkSize();
		long ok = 0;
		long ko = 0;
		int pagina = 0;
		List<Versamento> versamenti;
		do {
			versamenti = versamentoRepository.findVersamentiDaStampare(tracciato.getId(), PageRequest.of(pagina, PAGE_SIZE_STAMPE));

			// produzione PDF in parallelo per lotti
			List<CompletableFuture<List<RisultatoStampa>>> futures = new ArrayList<>();
			for (int i = 0; i < versamenti.size(); i += lotto) {
				List<Versamento> sottoInsieme = List.copyOf(versamenti.subList(i, Math.min(i + lotto, versamenti.size())));
				futures.add(CompletableFuture.supplyAsync(
						() -> sottoInsieme.stream().map(stampaAvvisoService::stampa).toList(), stampeTaskExecutor));
			}
			// aggregazione sequenziale nello ZIP
			for (CompletableFuture<List<RisultatoStampa>> future : futures) {
				for (RisultatoStampa risultato : future.join()) {
					if (risultato.ok()) {
						ok++;
					} else {
						ko++;
					}
					zipBuilder.aggiungi(risultato);
				}
			}
			pagina++;
		} while (versamenti.size() == PAGE_SIZE_STAMPE);

		byte[] zip = zipBuilder.build();
		beanDati.setNumStampeOk(ok);
		beanDati.setNumStampeKo(ko);
		// TODO(D4): persistenza dello ZIP su tracciati.zip_stampe (OID PostgreSQL / BLOB) via JDBC vendor-specific
		log.info("Stampe tracciato {}: {} ok, {} ko, ZIP di {} byte (persistenza zip_stampe da completare)",
				tracciato.getId(), ok, ko, zip.length);
	}

	// ── Step 5: finalizzazione (COMPLETATO dopo la stampa) ───────────────────

	@Bean
	public Step finalizzazioneStep(ElaborazioneTracciatoContext context,
			FinalizzazioneTracciatoService finalizzazioneService) {
		Tasklet tasklet = (contribution, chunkContext) -> {
			if (context.isPresente() && context.getTracciato().getStato() == StatoElaborazione.IN_STAMPA) {
				finalizzazioneService.completaStampa(context.getTracciato(), context.getBeanDati());
			}
			return RepeatStatus.FINISHED;
		};
		return new StepBuilder("finalizzazioneStep", this.jobRepository)
				.tasklet(tasklet, this.transactionManager).build();
	}
}

package com.zillya.timonfech.zillwrapper.apis;

import com.zillya.timonfech.zillwrapper.EntityTypeEnum;
import com.zillya.timonfech.zillwrapper.apis.enrichers.EnrichmentProgressRegistry;
import com.zillya.timonfech.zillwrapper.apis.enrichers.EnrichmentParallelismSettingsService;
import com.zillya.timonfech.zillwrapper.apis.enrichers.EnrichmentRequest;
import com.zillya.timonfech.zillwrapper.apis.enrichers.EntityUpdatedEvent;
import com.zillya.timonfech.zillwrapper.apis.enrichers.ExternalIdResolver;
import com.zillya.timonfech.zillwrapper.apis.enrichers.OrderEnrichmentMode;
import com.zillya.timonfech.zillwrapper.core.IEntityWithStatus;
import com.zillya.timonfech.zillwrapper.core.OperationResult;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.exceptions.OperationCancelledException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;


@Slf4j
public abstract class AbstractEntityEnrich<T extends IEntityWithStatus<?>> implements EntityEnricher {

    protected final ApplicationEventPublisher publisher;
    protected final EnrichmentProgressRegistry progressRegistry;
    protected final ExecutorService enrichmentTaskExecutor;
    protected final int defaultParallelism;
    @Autowired(required = false)
    private EnrichmentParallelismSettingsService parallelismSettingsService;

    protected AbstractEntityEnrich(
            ApplicationEventPublisher publisher,
            EnrichmentProgressRegistry progressRegistry,
            @Qualifier("enrichmentTaskExecutor") ExecutorService enrichmentTaskExecutor,
            @Value("${enrichment.parallelism:1}") int defaultParallelism
    ) {
        this.publisher = publisher;
        this.progressRegistry = progressRegistry;
        this.enrichmentTaskExecutor = enrichmentTaskExecutor;
        this.defaultParallelism = Math.max(1, defaultParallelism);
    }

    protected AbstractEntityEnrich(
            ApplicationEventPublisher publisher,
            EnrichmentProgressRegistry progressRegistry,
            @Qualifier("enrichmentTaskExecutor") ExecutorService enrichmentTaskExecutor
    ) {
        this(publisher, progressRegistry, enrichmentTaskExecutor, 1);
    }


    @Override
    public OperationResult<?> handle(EnrichmentRequest ctx, AtomicBoolean isCancelled) throws OperationCancelledException {
        try {
            process(ctx, resolver(), isCancelled);
            return OperationResult.ok(null);

        } catch (OperationCancelledException ce) {
            return OperationResult.fail("Op cancelled! " + ce.getMessage(), false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    protected abstract ExternalIdResolver resolver();

    protected void process(EnrichmentRequest ctx, ExternalIdResolver resolver, AtomicBoolean isCancelled) throws OperationCancelledException {

        LongStream stream;

        stream = resolver.resolve(ctx, () -> ctx.from() == -1 ? fetchLatestId() : ctx.from());

        PrimitiveIterator.OfLong iterator = stream.iterator();

        final int parallelism = Math.max(1, resolveParallelism(ctx));
        final int cancelCheckEvery = 1;


        AtomicInteger counter = new AtomicInteger();

        try {
            record InFlight(long externalId, Future<Boolean> future) {
            }
            List<InFlight> inFlight = new ArrayList<>();

            while (iterator.hasNext() || !inFlight.isEmpty()) {

                while (iterator.hasNext() && inFlight.size() < parallelism && !isCancelled.get()) {
                    long externalId = iterator.nextLong();

                    final long inFlightExternalId = externalId;
                    Future<Boolean> future = enrichmentTaskExecutor.submit(() -> {
                        if (isCancelled.get()) return true;

                        return processSingle(ctx, inFlightExternalId);
                    });

                    inFlight.add(new InFlight(inFlightExternalId, future));
                }

                Iterator<InFlight> it = inFlight.iterator();
                while (it.hasNext()) {
                    InFlight inFlightItem = it.next();
                    Future<Boolean> f = inFlightItem.future();

                    if (f.isDone()) {
                        boolean stop = f.get();
                        it.remove();
                        progressRegistry.tick(ctx.taskId(), inFlightItem.externalId());

                        if (counter.incrementAndGet() % cancelCheckEvery == 0) {
                            checkCancelled(ctx, isCancelled);
                        }

                        if (stop) {
                            isCancelled.set(true);
                            return;
                        }
                    }
                }
            }
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    public abstract String name();

    @Override
    public abstract T targetType();

    public long fetchLatestId() {
        return -1;
    }

    protected abstract boolean processSingle(EnrichmentRequest ctx, Long externalId);

    protected int resolveParallelism(EnrichmentRequest ctx) {
        if (parallelismSettingsService != null) {
            return Math.max(1, parallelismSettingsService.getLicenseParallelism());
        }
        return defaultParallelism;
    }

    protected void checkCancelled(EnrichmentRequest ctx, AtomicBoolean isCancelled) throws OperationCancelledException {
        if (isCancelled.get()) {
            throw new OperationCancelledException("Operation cancelled");
        }
    }

    protected Optional<Instant> parseToInstant(String dateStr) {
        if (dateStr == null || dateStr.isBlank() || dateStr.equals("-")) {
            return Optional.empty();
        }

        try {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            return Optional.of(LocalDateTime.parse(dateStr, dtf)
                    .atZone(ZoneOffset.UTC)
                    .toInstant());
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse date: {}", dateStr);
            return Optional.empty();
        }
    }

    protected void publishEntityUpdated(Long sourceId,
                                        EntityTypeEnum entityTypeEnum,
                                        Long entityId) {
        publishEntityUpdated(sourceId, entityTypeEnum, entityId, null);
    }

    protected void publishEntityUpdated(Long sourceId,
                                        EntityTypeEnum entityTypeEnum,
                                        Long entityId,
                                        OrderEnrichmentMode orderEnrichmentMode) {
        publisher.publishEvent(new EntityUpdatedEvent(
                this,
                sourceId,
                OperationType.ENTITY_ENRICHMENT,
                entityTypeEnum,
                entityId,
                Instant.now(),
                orderEnrichmentMode
        ));
    }
}

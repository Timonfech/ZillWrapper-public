package com.zillya.timonfech.zillwrapper.core.routing;

import com.zillya.timonfech.zillwrapper.core.IEntityWithStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.operation.IOperationContext;
import com.zillya.timonfech.zillwrapper.core.entities.source.InboundEvent;
import com.zillya.timonfech.zillwrapper.core.interfaces.IArtifact;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.*;

/**
 * Mutable context passed through the operation pipeline.
 * Carries pre-parsed data and tracks the current pipeline stage.
 */
@Getter
@Setter
@RequiredArgsConstructor
public class OrderOperationContext implements IOperationContext {

    private final Long sourceId;
    private final Long portalId;
    private final String email;
    private final List<OrderItemSpec> itemSpecs;
    private final List<DeliveryTargetSpec> deliveryTargets;
    private final InboundEvent<?> sourceContext;
    private List<String> emails = new ArrayList<>();

    private Long whiteAdminId;
    private String userComment;
    private String waDocAddress;
    private String waComment;
    private Boolean partnerOverride;
    private String commandPayload;
    private Long initiatorUserId;

    @Setter
    private Long orderId;

    @Setter
    private BigInteger operationId;

    @Setter
    private BigInteger stageExecutionId;

    @Setter
    private OperationType currentStage = OperationType.ORDER_CREATION;
    private boolean skipDuplicateCheck = false;
    private boolean payedReady = false;
    private boolean includeLegacySync = true;
    private String localeTag;

    /**
     * Sequence of upcoming stages in the pipeline.
     */
    private final Queue<OperationType> pipelinePlan = new LinkedList<>();

    private final List<IArtifact> artifacts = new ArrayList<>();
    private final Map<Long, List<IArtifact>> itemArtifacts = new HashMap<>();
    private final List<String> nonCriticalWarnings = new ArrayList<>();

    @Override
    public List<IArtifact> getArtifacts() {
        return artifacts;
    }

    @Override
    public void addArtifact(IArtifact artifact) {
        this.artifacts.add(artifact);
    }

    public void addItemArtifact(Long orderItemId, IArtifact artifact) {
        if (orderItemId == null || artifact == null) {
            return;
        }
        this.artifacts.add(artifact);
        this.itemArtifacts.computeIfAbsent(orderItemId, k -> new ArrayList<>()).add(artifact);
    }

    public void addWarning(String warning) {
        if (warning != null && !warning.isBlank()) {
            this.nonCriticalWarnings.add(warning);
        }
    }

    public void replacePipelinePlan(List<OperationType> stages) {
        this.pipelinePlan.clear();
        if (stages == null || stages.isEmpty()) {
            return;
        }
        this.pipelinePlan.addAll(stages);
    }

    public String primaryEmail() {
        if (email != null && !email.isBlank()) {
            return email;
        }
        if (emails == null || emails.isEmpty()) {
            return null;
        }
        String first = emails.getFirst();
        return first == null || first.isBlank() ? null : first;
    }

    @Override
    public Long getEntitySourceId() {
        return sourceId;
    }

    @Override
    @Nullable
    public BigInteger getOperationId() {
        return operationId;
    }

    @Override
    public Long getEntityId() {
        return portalId;
    }

    @Override
    public IEntityWithStatus<?> getIEntityWithStatus() {
        return null;
    }

    @Override
    public OperationType getOperationType() {
        return currentStage;
    }

    @Override
    public Long getInitiatorUserId() {
        return initiatorUserId;
    }

    @Override
    public InboundEvent<?> getSourceContext() {
        return sourceContext;
    }
}

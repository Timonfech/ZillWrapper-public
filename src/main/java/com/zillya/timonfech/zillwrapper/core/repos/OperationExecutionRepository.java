package com.zillya.timonfech.zillwrapper.core.repos;

import com.zillya.timonfech.zillwrapper.core.OperationStatus;
import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionEntity;
import com.zillya.timonfech.zillwrapper.core.entities.operation.OperationExecutionKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

public interface OperationExecutionRepository extends JpaRepository<OperationExecutionEntity, BigInteger> {
    Optional<OperationStatus> findStatusById(@Param("id") BigInteger id);

    List<OperationExecutionEntity> findByParentId(BigInteger parentId);

    @Query("""
            select o from OperationExecutionEntity o
            where o.parentId = :parentId
            order by
              case when o.sequenceNo is null then 1 else 0 end,
              o.sequenceNo asc,
              o.createdAt asc
            """)
    List<OperationExecutionEntity> findOrderedChildren(@Param("parentId") BigInteger parentId);

    Optional<OperationExecutionEntity> findByIdAndExecutionKind(BigInteger id, OperationExecutionKind executionKind);

    List<OperationExecutionEntity> findByExecutionKindAndOperationTypeAndStatus(OperationExecutionKind executionKind,
                                                                                OperationType operationType,
                                                                                OperationStatus status);

    List<OperationExecutionEntity> findByEntityIdAndOperationTypeOrderByCreatedAtDesc(Long entityId, OperationType operationType);
}

package com.zillya.timonfech.zillwrapper.core.repos;

import com.zillya.timonfech.zillwrapper.core.entities.ItemProcessingStatus;
import com.zillya.timonfech.zillwrapper.core.entities.order.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, Long> {
    List<OrderItemEntity> findByOrderId(Long orderId);
    List<OrderItemEntity> findByOrderIdOrderByIdAsc(Long orderId);
    Optional<OrderItemEntity> findById(Long id);

    @Modifying
    @Transactional
    @Query("update OrderItemEntity i set i.processingStatus = :status where i.id = :id")
    int updateProcessingStatusById(@Param("id") Long id, @Param("status") com.zillya.timonfech.zillwrapper.core.entities.ItemProcessingStatus status);

    @Query("select count(i) from OrderItemEntity i where i.id in :ids and i.processingStatus in :statuses")
    long countByIdInAndProcessingStatusIn(@Param("ids") List<Long> ids,
                                          @Param("statuses") List<ItemProcessingStatus> statuses);
}

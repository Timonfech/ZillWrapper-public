package com.zillya.timonfech.zillwrapper.core.repos;

import com.zillya.timonfech.zillwrapper.core.entities.order.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    Optional<OrderEntity> findByWhiteAdminId(Long whiteAdminId);

    Optional<OrderEntity> findByIdOrWhiteAdminId(Long id, Long whiteAdminId);

    Optional<OrderEntity> findByPortalId(Long portalId);
    List<OrderEntity> findAllByPortalId(Long portalId);
    List<OrderEntity> findAllByWhiteAdminId(Long whiteAdminId);
    List<OrderEntity> findAllByUserComment(String userComment);

    @Query("select max(o.whiteAdminId) from OrderEntity o where o.whiteAdminId is not null")
    Long findMaxWhiteAdminId();

    @Query("select max(o.portalId) from OrderEntity o where o.portalId is not null")
    Long findMaxPortalId();

    @Query("""
            select o from OrderEntity o
            where lower(trim(coalesce(o.userComment, ''))) = lower(trim(:comment))
            """)
    List<OrderEntity> findAllByUserCommentNormalized(@Param("comment") String comment);

    @Query(value = """
            select exists (
                select 1
                from order_delivery_targets odt
                join contact_method cm on cm.id = odt.contact_id
                join email_contact ec on ec.id = cm.id
                where odt.order_id = :orderId
                  and odt.enabled = true
                  and lower(trim(cast(coalesce(ec.encrypted_value, '') as text))) = lower(trim(cast(:email as text)))
            )
            """, nativeQuery = true)
    boolean hasEmailDeliveryTarget(@Param("orderId") Long orderId, @Param("email") String email);

    @Query(value = """
            select distinct odt.order_id
            from order_delivery_targets odt
            join contact_method cm on cm.id = odt.contact_id
            join email_contact ec on ec.id = cm.id
            where odt.enabled = true
              and lower(trim(cast(coalesce(ec.encrypted_value, '') as text))) = lower(trim(cast(:email as text)))
            """, nativeQuery = true)
    List<Long> findOrderIdsByEmailDeliveryTarget(@Param("email") String email);

    @Query(value = """
            with input_items as (
                select
                    x.pc_per_license::int as pc_per_license,
                    x.lic_count::int as lic_count,
                    x.period_amount::int as period_amount,
                    x.period_unit::text as period_unit,
                    coalesce((select array_agg(v order by v)
                              from unnest(coalesce(x.key_types, array[]::text[])) v), array[]::text[]) as key_types
                from jsonb_to_recordset(cast(:itemsJson as jsonb))
                    as x(pc_per_license int, lic_count int, period_amount int, period_unit text, key_types text[])
            ),
            input_group as (
                select pc_per_license, lic_count, period_amount, period_unit, key_types, count(*) as cnt
                from input_items
                group by pc_per_license, lic_count, period_amount, period_unit, key_types
            ),
            db_items as (
                select
                    oi.order_id as order_id,
                    oi.pc_per_license as pc_per_license,
                    oi.lic_count as lic_count,
                    oi.period_amount as period_amount,
                    oi.period_unit as period_unit,
                    coalesce((select array_agg(v order by v)
                              from jsonb_array_elements_text(coalesce(oi.key_types, '[]'::jsonb)) v), array[]::text[]) as key_types
                from order_items oi
                where oi.order_id = :orderId
                group by oi.id, oi.order_id, oi.pc_per_license, oi.lic_count, oi.period_amount, oi.period_unit, oi.key_types
            ),
            db_group as (
                select order_id, pc_per_license, lic_count, period_amount, period_unit, key_types, count(*) as cnt
                from db_items
                group by order_id, pc_per_license, lic_count, period_amount, period_unit, key_types
            )
            select
                (
                    not exists (
                        select pc_per_license, lic_count, period_amount, period_unit, key_types, cnt from input_group
                        except
                        select pc_per_license, lic_count, period_amount, period_unit, key_types, cnt from db_group
                    )
                )
                and
                (
                    not exists (
                        select pc_per_license, lic_count, period_amount, period_unit, key_types, cnt from db_group
                        except
                        select pc_per_license, lic_count, period_amount, period_unit, key_types, cnt from input_group
                    )
            )
            """, nativeQuery = true)
    /**
     * @deprecated Order duplicate detection uses {@link #containsOrderItems(Long, String)}
     * to allow "new request is subset of existing order" behavior.
     * Kept only for backward compatibility and potential diagnostics.
     */
    @Deprecated(since = "0.0.2", forRemoval = false)
    boolean matchesOrderItemsExactly(@Param("orderId") Long orderId, @Param("itemsJson") String itemsJson);

    @Query(value = """
            with input_items as (
                select
                    x.pc_per_license::int as pc_per_license,
                    x.lic_count::int as lic_count,
                    x.period_amount::int as period_amount,
                    x.period_unit::text as period_unit,
                    coalesce((select array_agg(v order by v)
                              from unnest(coalesce(x.key_types, array[]::text[])) v), array[]::text[]) as key_types
                from jsonb_to_recordset(cast(:itemsJson as jsonb))
                    as x(pc_per_license int, lic_count int, period_amount int, period_unit text, key_types text[])
            ),
            input_group as (
                select pc_per_license, lic_count, period_amount, period_unit, key_types, count(*) as cnt
                from input_items
                group by pc_per_license, lic_count, period_amount, period_unit, key_types
            ),
            db_items as (
                select
                    oi.order_id as order_id,
                    oi.pc_per_license as pc_per_license,
                    oi.lic_count as lic_count,
                    oi.period_amount as period_amount,
                    oi.period_unit as period_unit,
                    coalesce((select array_agg(v order by v)
                              from jsonb_array_elements_text(coalesce(oi.key_types, '[]'::jsonb)) v), array[]::text[]) as key_types
                from order_items oi
                where oi.order_id = :orderId
                group by oi.id, oi.order_id, oi.pc_per_license, oi.lic_count, oi.period_amount, oi.period_unit, oi.key_types
            ),
            db_group as (
                select order_id, pc_per_license, lic_count, period_amount, period_unit, key_types, count(*) as cnt
                from db_items
                group by order_id, pc_per_license, lic_count, period_amount, period_unit, key_types
            )
            select not exists (
                select 1
                from input_group ig
                left join db_group dg
                  on dg.pc_per_license = ig.pc_per_license
                 and dg.lic_count = ig.lic_count
                 and dg.period_amount = ig.period_amount
                 and dg.period_unit = ig.period_unit
                 and dg.key_types = ig.key_types
                where dg.order_id is null or dg.cnt < ig.cnt
            )
            """, nativeQuery = true)
    boolean containsOrderItems(@Param("orderId") Long orderId, @Param("itemsJson") String itemsJson);

    @Query("""
            select distinct o from OrderEntity o
            left join fetch o.client c
            left join fetch o.deliveryTargets dt
            left join fetch dt.contactMethod cm
            where o.id = :id
            """)
    Optional<OrderEntity> findByIdWithDeliveryTargets(@Param("id") Long id);

    @Query("""
            select distinct o from OrderEntity o
            left join fetch o.items i
            where o.id = :id
            """)
    Optional<OrderEntity> findByIdWithItems(@Param("id") Long id);

    @Query("""
            select o from OrderEntity o
            left join fetch o.client c
            where o.id = :id
            """)
    Optional<OrderEntity> findByIdWithClient(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("update OrderEntity o set o.legalEntityInfoJson = :legalEntityInfoJson where o.id = :orderId")
    int updateLegalEntityInfoJsonById(@Param("orderId") Long orderId,
                                      @Param("legalEntityInfoJson") String legalEntityInfoJson);

}

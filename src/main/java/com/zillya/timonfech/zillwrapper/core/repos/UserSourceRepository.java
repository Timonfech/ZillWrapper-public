package com.zillya.timonfech.zillwrapper.core.repos;

import com.zillya.timonfech.zillwrapper.core.entities.security.UserSourceEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserSourceRepository extends JpaRepository<UserSourceEntity, Long> {

    List<UserSourceEntity> findBySource_Id(Long sourceId);

    @Query("""
        select distinct us
        from UserSourceEntity us
        left join fetch us.requiredFactors
        join fetch us.user
        where us.source.id = :sourceId
        """)
    List<UserSourceEntity> findBySourceIdWithFactors(@Param("sourceId") Long sourceId);

    @Query("""
        SELECT us.user FROM UserSourceEntity us\s
        JOIN us.requiredFactors factors\s
        WHERE us.source.id = :sourceId\s
        AND KEY(factors) = :factorType\s
        AND VALUE(factors) = :factorValue\s
        AND us.user.isActive = true
   \s""")
    Optional<UserEntity> findActiveUserByFactor(
            @Param("sourceId") Long sourceId, // например ID конкретного бота
            @Param("factorType") UserSourceEntity.SecurityFactor factorType,
            @Param("factorValue") String factorValue
    );

}

package com.zillya.timonfech.zillwrapper.core.repos;

import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethod;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends CrudRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findByContacts(List<ContactMethod> contacts);

    @Query("""
            select u from UserEntity u
            where u.role = com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity.Role.ADMIN
              and u.isActive = true
            order by u.id asc
            """)
    List<UserEntity> findActiveAdmins();

    @Query("""
            select distinct u from UserEntity u
            left join fetch u.contacts c
            where u.id = :id
            """)
    Optional<UserEntity> findByIdWithContacts(@Param("id") Long id);
}

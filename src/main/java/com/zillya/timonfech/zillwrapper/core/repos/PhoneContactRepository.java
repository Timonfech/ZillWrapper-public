package com.zillya.timonfech.zillwrapper.core.repos;

import com.zillya.timonfech.zillwrapper.core.entities.user_clients.PhoneContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PhoneContactRepository extends JpaRepository<PhoneContact, Long> {
    Optional<PhoneContact> findByValueHash(String valueHash);
    List<PhoneContact> findAllByValueHash(String valueHash);
}
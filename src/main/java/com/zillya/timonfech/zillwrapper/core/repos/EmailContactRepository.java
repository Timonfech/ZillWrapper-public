package com.zillya.timonfech.zillwrapper.core.repos;

import com.zillya.timonfech.zillwrapper.core.entities.user_clients.EmailContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailContactRepository extends JpaRepository<EmailContact, Long> {
    Optional<EmailContact> findByValueHash(String valueHash);

    List<EmailContact> findAllByValueHash(String valueHash);

    Optional<EmailContact> findByEncryptedValueIgnoreCase(String encryptedValue);

    List<EmailContact> findAllByEncryptedValueIgnoreCase(String encryptedValue);
}

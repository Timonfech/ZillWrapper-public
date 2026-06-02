package com.zillya.timonfech.zillwrapper.core.repos;

import com.zillya.timonfech.zillwrapper.core.entities.user_clients.TelegramContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TelegramContactRepository extends JpaRepository<TelegramContact, Long> {
    Optional<TelegramContact> findByValueHash(String valueHash);
    List<TelegramContact> findAllByValueHash(String valueHash);
}

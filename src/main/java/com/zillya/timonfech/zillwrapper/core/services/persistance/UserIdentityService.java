package com.zillya.timonfech.zillwrapper.core.services.persistance;

import com.zillya.timonfech.zillwrapper.core.entities.security.UserSourceEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import com.zillya.timonfech.zillwrapper.core.repos.UserSourceRepository;
import com.zillya.timonfech.zillwrapper.security.CryptoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserIdentityService {

    private final UserSourceRepository userSourceRepository;
    private final CryptoUtils cryptoUtils;


    @Transactional(readOnly = true)
    public Optional<UserEntity> findActiveUser(Long sourceId,
                                               UserSourceEntity.SecurityFactor factorType,
                                               String plainFactorValue) throws Exception {

        String hashedValue = cryptoUtils.hmacSha256Base64(plainFactorValue.toLowerCase());

        return userSourceRepository.findActiveUserByFactor(sourceId, factorType, hashedValue);
    }

    @Transactional(readOnly = true)
    public UserEntity requireActiveUser(Long sourceId,
                                        UserSourceEntity.SecurityFactor factorType,
                                        String plainFactorValue) throws Exception {
        return findActiveUser(sourceId, factorType, plainFactorValue)
                .orElseThrow(() -> new IllegalArgumentException("Active user not found or access denied"));
    }
}

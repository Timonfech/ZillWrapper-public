package com.zillya.timonfech.zillwrapper.core.entities.security;

import com.zillya.timonfech.zillwrapper.core.entities.source.SourceEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import com.zillya.timonfech.zillwrapper.core.repos.UserSourceRepository;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;
import com.zillya.timonfech.zillwrapper.security.CryptoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApiKeyProvisioningService {

    private final CryptoUtils cryptoUtils;
    private final UserSourceRepository sourceRepository;

    @Transactional
    public String generateAndSaveApiKey(UserEntity user, SourceEntity source, String apiUsername) throws Exception {
        String plainApiKey = "sk_live_" + UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().substring(0, 10);

        String salt = cryptoUtils.generateSalt();
        String hash = cryptoUtils.hashPassword(plainApiKey, salt);

        Map<UserSourceEntity.SecurityFactor, String> factors = new HashMap<>();
        factors.put(UserSourceEntity.SecurityFactor.API_USERNAME, apiUsername);
        factors.put(UserSourceEntity.SecurityFactor.API_KEY_SALT, salt);
        factors.put(UserSourceEntity.SecurityFactor.API_KEY_HASH, hash);

        UserSourceEntity userSource = new UserSourceEntity(user, SourceType.API, factors);
        userSource.setSource(source);

        sourceRepository.save(userSource);

        return plainApiKey;
    }
}
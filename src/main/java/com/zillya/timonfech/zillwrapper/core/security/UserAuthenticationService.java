package com.zillya.timonfech.zillwrapper.core.security;

import com.zillya.timonfech.zillwrapper.core.entities.security.AuthErrorReason;
import com.zillya.timonfech.zillwrapper.core.entities.security.Identity;
import com.zillya.timonfech.zillwrapper.core.entities.security.UserSourceEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import com.zillya.timonfech.zillwrapper.core.exceptions.AuthenticationException;
import com.zillya.timonfech.zillwrapper.core.repos.UserSourceRepository;
import com.zillya.timonfech.zillwrapper.security.CryptoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuthenticationService {

    private final UserSourceRepository sourceRepository;
    private final CryptoUtils cryptoUtils;
    private final UserSourceCacheService cacheService;

    public UserEntity authenticate(Identity identity) {
        return cacheService.getAuthenticatedUser(identity, () -> performFullAuthentication(identity));
    }

    private UserEntity performFullAuthentication(Identity identity) {
        List<UserSourceEntity> candidates = sourceRepository.findBySourceIdWithFactors(identity.sourceId());

        if (candidates.isEmpty()) {
            throw new AuthenticationException(
                    AuthErrorReason.SOURCE_NOT_FOUND,
                    identity.sourceId(),
                    "No authorized users configured for this source ID."
            );
        }

        UserSourceEntity matchedSource = candidates.stream()
                .filter(candidate -> matchesAllFactors(candidate, identity))
                .findFirst()
                .orElseThrow(() -> new AuthenticationException(
                        AuthErrorReason.USER_NOT_FOUND,
                        identity.sourceId(),
                        identity.factors(),
                        "Access denied: No user matches the provided identity factors for this source."
                ));

        //  Security Check: User Status
        UserEntity user = matchedSource.getUser();
        if (!user.isActive()) {
            throw new AuthenticationException(
                    AuthErrorReason.USER_INACTIVE,
                    identity.sourceId(),
                    "The user account is currently disabled."
            );
        }

        return user;
    }

    private boolean matchesAllFactors(UserSourceEntity source, Identity identity) {
        Map<UserSourceEntity.SecurityFactor, String> required = source.getRequiredFactors();
        Map<UserSourceEntity.SecurityFactor, String> provided = identity.factors();

        if (required.isEmpty()) return false;

        for (Map.Entry<UserSourceEntity.SecurityFactor, String> entry : required.entrySet()) {
            UserSourceEntity.SecurityFactor dbFactorType = entry.getKey();
            String dbValue = entry.getValue();

            if (dbFactorType == UserSourceEntity.SecurityFactor.API_KEY_HASH ||
                    dbFactorType == UserSourceEntity.SecurityFactor.API_KEY_SALT) {
                continue;
            }

            String providedValue = provided.get(dbFactorType);
            if (!dbValue.equals(providedValue)) {
                return false;
            }
        }

        if (required.containsKey(UserSourceEntity.SecurityFactor.API_KEY_HASH)) {
            String dbHash = required.get(UserSourceEntity.SecurityFactor.API_KEY_HASH);
            String dbSalt = required.get(UserSourceEntity.SecurityFactor.API_KEY_SALT);
            String providedPlainKey = provided.get(UserSourceEntity.SecurityFactor.PLAIN_API_KEY);

            if (dbHash == null || dbSalt == null || providedPlainKey == null) {
                return false;
            }

            try {
                if (!cryptoUtils.verifyPassword(providedPlainKey, dbSalt, dbHash)) {
                    return false;
                }
            } catch (Exception e) {
                log.error("Crypto error during password verification", e);
                return false;
            }
        }

        return true;
    }
}

package com.zillya.timonfech.zillwrapper.api.auth;

import com.zillya.timonfech.zillwrapper.api.ApiException;
import com.zillya.timonfech.zillwrapper.core.entities.security.BaseIdentity;
import com.zillya.timonfech.zillwrapper.core.entities.security.UserSourceEntity;
import com.zillya.timonfech.zillwrapper.core.security.UserAuthenticationService;
import com.zillya.timonfech.zillwrapper.core.services.SourceManagementService;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ApiAuthenticationService {

    private final UserAuthenticationService userAuthenticationService;
    private final SourceManagementService sourceManagementService;

    @Value("${api.auth.source-identifier:api}")
    private String apiSourceIdentifier;

    public ApiPrincipal authenticate(HttpServletRequest request) {
        String apiUser = header(request, "X-API-User");
        String apiKey = header(request, "X-API-Key");
        if (apiUser == null || apiUser.isBlank() || apiKey == null || apiKey.isBlank()) {
            throw ApiException.unauthorized("Missing API credentials");
        }

        var source = sourceManagementService.getOrCreateSource(SourceType.API, apiSourceIdentifier);
        var identity = new BaseIdentity(
                source.getId(),
                SourceType.API,
                Map.of(
                        UserSourceEntity.SecurityFactor.API_USERNAME, apiUser.trim(),
                        UserSourceEntity.SecurityFactor.PLAIN_API_KEY, apiKey
                )
        );

        var user = userAuthenticationService.authenticate(identity);
        return new ApiPrincipal(user.getId(), user.getUsername(), source.getId(), user.getRole());
    }

    private String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null ? null : value.trim();
    }
}

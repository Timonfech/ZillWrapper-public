package com.zillya.timonfech.zillwrapper.api;

import com.zillya.timonfech.zillwrapper.api.auth.ApiAccessPolicyService;
import com.zillya.timonfech.zillwrapper.api.auth.ApiAuthenticationService;
import com.zillya.timonfech.zillwrapper.api.auth.ApiPrincipal;
import com.zillya.timonfech.zillwrapper.api.masking.MaskingVaultService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/masking")
@RequiredArgsConstructor
public class MaskingController {

    private final ApiAuthenticationService apiAuthenticationService;
    private final ApiAccessPolicyService accessPolicyService;
    private final MaskingVaultService maskingVaultService;

    @PostMapping("/session/start")
    public StartSessionResponse startSession(@RequestBody(required = false) StartSessionRequest request,
                                             HttpServletRequest httpRequest) {
        ApiPrincipal principal = apiAuthenticationService.authenticate(httpRequest);
        accessPolicyService.requireMaskingSessionAccess(principal);
        String purpose = request != null ? request.purpose() : null;
        if (purpose == null || purpose.isBlank()) {
            purpose = principal.isLlmReadonly() ? "llm_readonly" : "api_read";
        }
        MaskingVaultService.SessionInfo session = maskingVaultService.startSession(principal.userId(), purpose);
        return new StartSessionResponse(
                session.maskingSessionId(),
                Map.of(
                        "mode", session.mode(),
                        "inactivitySeconds", session.inactivitySeconds(),
                        "llmOnlyByDefault", true
                )
        );
    }

    @PostMapping("/resolve")
    public ResolveResponse resolve(@RequestBody ResolveRequest request,
                                   HttpServletRequest httpRequest) {
        if (request == null || request.maskingSessionId() == null || request.tokens() == null) {
            throw ApiException.badRequest("maskingSessionId and tokens[] are required");
        }
        ApiPrincipal principal = apiAuthenticationService.authenticate(httpRequest);
        accessPolicyService.requireMaskingSessionAccess(principal);
        MaskingVaultService.ResolveResult result = maskingVaultService.resolve(
                request.maskingSessionId(),
                principal.userId(),
                request.tokens()
        );
        log.info("masking_resolve endpoint=/api/v1/masking/resolve userId={} tokens={} resolved={} missing={} expired={}",
                principal.userId(),
                request.tokens().size(),
                result.resolved().size(),
                result.missing().size(),
                result.expired().size());
        return new ResolveResponse(result.resolved(), result.missing(), result.expired());
    }

    @PostMapping("/session/close")
    public CloseSessionResponse close(@RequestBody CloseSessionRequest request,
                                      HttpServletRequest httpRequest) {
        if (request == null || request.maskingSessionId() == null || request.maskingSessionId().isBlank()) {
            throw ApiException.badRequest("maskingSessionId is required");
        }
        ApiPrincipal principal = apiAuthenticationService.authenticate(httpRequest);
        accessPolicyService.requireMaskingSessionAccess(principal);
        maskingVaultService.closeOwnedSession(request.maskingSessionId(), principal.userId());
        return new CloseSessionResponse("closed");
    }

    public record StartSessionRequest(String purpose) {
    }

    public record ResolveRequest(String maskingSessionId, List<String> tokens) {
    }

    public record CloseSessionRequest(String maskingSessionId) {
    }

    public record StartSessionResponse(String maskingSessionId, Map<String, Object> policy) {
    }

    public record ResolveResponse(Map<String, String> resolved, List<String> missing, List<String> expired) {
    }

    public record CloseSessionResponse(String status) {
    }
}

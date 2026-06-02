# Core Security Model

## Owned folders
- `src/main/java/com/zillya/timonfech/zillwrapper/core/security`
- `src/main/java/com/zillya/timonfech/zillwrapper/core/entities/security`
- `src/main/java/com/zillya/timonfech/zillwrapper/api/auth`
- `src/main/java/com/zillya/timonfech/zillwrapper/security`

## Primary classes/interfaces
- `UserAuthenticationService`
- `OperationAuthorizationService`
- `UserSourceEntity.SecurityFactor`
- `ApiKeyProvisioningService`
- `ApiAuthenticationService`
- `ApiAccessPolicyService`
- `CryptoUtils`
- `UserSourceCacheService`

## Runtime flow summary
Authentication is source-factor based (`UserSourceEntity` + required factors), then role checks gate operation start and API access.  
API key provisioning stores derived hash+salt factors through `ApiKeyProvisioningService`; runtime auth is verified by `UserAuthenticationService`.  
Operation-level authorization is enforced in `OperationAuthorizationService`, while API read/masking policies are enforced by `ApiAccessPolicyService`.

## Roles, permissions, and quotas
- Different users can operate with different permissions depending on role, bound source, and configured security factors.
- Operation rights are not uniform: `OperationAuthorizationService` and `OrderSecurityService` can allow or deny creation, mutation, resend, and other flows per user context.
- Product access and quantity limits are enforced through `ProductQuotaEntity` and the quota services used by order/pipeline security paths.
- API access is also role-sensitive: current read endpoints are allowed for `ADMIN`, `MANAGER`, and `LLM_READONLY`, while masking access follows `ApiAccessPolicyService` rules.

## Extension points
- Add security factors in `UserSourceEntity.SecurityFactor` and matching rules in `UserAuthenticationService`.
- Extend API policy by role/scopes in `ApiAccessPolicyService`.
- Keep cryptographic primitives centralized in `CryptoUtils`; avoid business-layer crypto duplication.

## Runtime configuration before startup
- Configure security-related properties via environment variables before first run (API auth source, credentials, mail transport, bot tokens, and DB access).
- Current authorization model is service-level (`UserAuthenticationService`, `OperationAuthorizationService`, `ApiAccessPolicyService`), so deployment config must keep these paths enabled.
- Encryption-at-rest for contact values is optional in the current architecture and can be enabled/expanded through `CryptoUtils` integration policy.

## Vulnerability reporting
- Do not report security-sensitive findings via public issue trackers.
- Report privately to maintainers with:
- impact summary
- reproduction steps
- affected commit/version
- proposed mitigation (if available)

## Secrets and configuration hygiene
- Keep credentials, API keys, tokens, and private certificates only in environment variables or deployment secret stores.
- Never commit runtime identity files (for example `users.json`) or local override files (`.env.local`).
- Use template files (`.env.example`, `*.template.*`) for documentation-only defaults.

## Repository hygiene
- Keep pre-commit checks enabled (`lefthook`, secret scanning where configured).
- Review staged diff for accidental plaintext secrets before each push.
- Keep crash dumps and local runtime artifacts excluded from version control.

## Related docs
- [api-roadmap.md](api-roadmap.md)

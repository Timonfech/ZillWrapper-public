# API Roadmap

## Owned folders
- `src/main/java/com/zillya/timonfech/zillwrapper/api`
- `src/main/java/com/zillya/timonfech/zillwrapper/api/auth`
- `src/main/java/com/zillya/timonfech/zillwrapper/api/masking`

## Primary classes/interfaces
- `ReadController`
- `MaskingController`
- `ApiAuthenticationService`
- `ApiAccessPolicyService`
- `ApiPrincipal`
- `MaskingVaultService`
- `ApiExceptionHandler`
- `ApiException`

## Runtime flow summary
Current API supports authenticated read operations (`/api/v1/orders`, `/api/v1/licenses`) and masking session workflows (`/api/v1/masking/*`).  
`ApiAuthenticationService` authenticates API principals through source-factor identity, then `ApiAccessPolicyService` enforces role-level policy.  
Masking is session-bound through `MaskingVaultService` and resolves placeholders via controlled batch endpoints.

## Current access model
- Read endpoints are available to `ADMIN`, `MANAGER`, and `LLM_READONLY`.
- Masking session endpoints are always available to `LLM_READONLY`.
- Human masking access for `ADMIN` and `MANAGER` is optional and controlled by `api.masking.allow-human`.
- List endpoints bound `limit` to `1..500` records per request.

## Current v1 endpoints
- `GET /api/v1/orders`
- `GET /api/v1/orders/{id}`
- `GET /api/v1/licenses`
- `GET /api/v1/licenses/{id}`
- `POST /api/v1/masking/session/start`
- `POST /api/v1/masking/resolve`
- `POST /api/v1/masking/session/close`

## LLM masking policy (current contract)
- `LLM_READONLY` role is read-only and cannot call mutation operations.
- For `LLM_READONLY`, masking mode is enforced and sensitive values are returned as placeholders.
- Placeholder resolution is session-bound and owner-bound; cross-session resolution is denied.
- `MaskingVaultService` keeps runtime token mapping in memory for active sessions.

## Extension points
- Add mutation endpoints only with explicit policy gates aligned with `OperationAuthorizationService`.
- Keep API orchestration aligned with core pipeline handlers to avoid business-logic duplication in controllers.
- Move from in-memory masking runtime to persistent/cluster-safe backend if multi-node API usage becomes primary.

## Current status
- Current public endpoints cover authenticated read access for orders and licenses, plus masking sessions for LLM-oriented read flows.
- API-side generation, modify-status, resend, and other mutation endpoints are planned separately and are not part of the current controller set.
- Core quota checks already exist in pipeline/security services; dedicated API contracts for quota-aware generation flows are still pending.

## Related docs
- [core-security-model.md](core-security-model.md)
- [core-pipeline.md](core-pipeline.md)

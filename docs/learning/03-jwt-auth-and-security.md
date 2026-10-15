# 03 — JWT authentication, Spring Security, error handling

## What we built
- `POST /api/auth/register` (creates a MEMBER) and `POST /api/auth/login` (returns a JWT).
- `JwtService` (issue + verify), `JwtAuthenticationFilter` (reads `Authorization: Bearer …`), `SecurityConfig`.
- `GET /api/users/me`, `PATCH /api/users/{id}/role` (ADMIN only, via `@PreAuthorize`).
- `AdminBootstrap`: creates the first admin from env vars.
- `GlobalExceptionHandler`: every error is RFC 7807 `application/problem+json`.

## How a request is authenticated

```
client ──► JwtAuthenticationFilter ──► authorization rules ──► controller
             │ valid token?                │ authenticated?
             ├─ yes: SecurityContext = user├─ no  → 401 (entry point)
             └─ no: stay anonymous         └─ yes, but wrong role → 403 (access denied handler)
```

## Key ideas

**JWT anatomy.** `header.payload.signature`, each part base64url-encoded. The payload (`sub`, `email`, `role`, `iat`, `exp`)
is **readable by anyone**: it is signed, not encrypted, so never put secrets in it. The signature is
HMAC-SHA256(header + payload, server secret). Changing one byte of the payload breaks the signature (see `tamperedTokenIsRejected`).

**Stateless.** The server stores no session. Every request carries its own proof. Pros: horizontal scaling, no session store.
Cons: you can't easily *revoke* a token before it expires, and a role change only takes effect on the next login.
Mitigations: short expiry + refresh tokens, or a deny-list in Redis.

**BCrypt.** A slow, salted hash. The salt makes identical passwords produce different hashes, which defeats rainbow tables.
Slowness (cost factor) makes brute force expensive. Passwords are never decrypted, only re-hashed and compared.

**Don't leak information.** Login returns the same "Invalid email or password" for an unknown email *and* a wrong password.
For an unknown email it still runs a BCrypt comparison against a dummy hash, so the response time doesn't reveal whether the email exists.

**Why CSRF is disabled.** CSRF attacks rely on the browser *automatically* attaching cookies. Our token lives in a header the
attacker's page can't set, so there's nothing to forge.

**401 vs 403.** 401 = "who are you?" (no or invalid token). 403 = "I know who you are, and you can't do this."

**Two layers of authorization.** URL rules in `SecurityConfig` (public vs authenticated) and method rules with
`@PreAuthorize("hasRole('ADMIN')")`. `hasRole('ADMIN')` checks for the authority `ROLE_ADMIN`, which is why the filter adds the `ROLE_` prefix.

**Errors from filters.** Exceptions thrown in the security filter chain happen *before* Spring MVC, so `@RestControllerAdvice`
never sees them. That's why `SecurityConfig` writes the 401/403 JSON itself.

**Injected `Clock`.** `JwtService` asks a `Clock` for the time instead of calling `Instant.now()`. Tests pass a fixed clock and
can test expiry without sleeping.

## Try it
```bash
./mvnw spring-boot:run
curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"fullName":"Ada","email":"ada@example.com","password":"password123"}'
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ada@example.com","password":"password123"}' | jq -r .accessToken)
curl -s localhost:8080/api/users/me -H "Authorization: Bearer $TOKEN"
```
Paste the token into https://jwt.io to see the claims.

## Interview questions
1. Where should a browser app store a JWT, and what are the XSS/CSRF trade-offs of localStorage vs an HttpOnly cookie?
2. How would you log a user out "everywhere" with stateless JWTs?
3. Why is HS256 fine here, and when would you choose RS256? *(RS256: other services verify with a public key without being able to mint tokens.)*
4. What is the difference between authentication and authorization?

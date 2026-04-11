# Part 1: Auth Service (JWT)

**Time estimate:** 12–16 hours  
**Goal:** Build a standalone authentication service that issues and manages JWTs — the identity backbone every other service in this system will depend on.

**What you're building:**
A REST service that does exactly this:
- A user registers with username/password (you've done this before — BCrypt, validation, the works)
- A user logs in, and instead of creating a server-side session like the URL shortener, the service hands back two tokens: a short-lived access token and a long-lived refresh token
- Any other service in the system can take that access token, verify the signature locally, and know who the user is — without ever calling back to this service
- When the access token expires, the client uses the refresh token to get a new one — and that refresh token lives in Redis so you can revoke it

**What's reinforcement vs. what's new:**
You already know how to stand up a Spring Boot service with PostgreSQL, write REST controllers, hash passwords with BCrypt, validate DTOs, and wire up Redis. That's ~40% of this service. The new ground is JWT mechanics, the dual-token pattern, and rethinking your auth filter to be stateless.

---

## Project Context: Why This Service Exists

Before you write a line of code, understand the architectural role. In the URL shortener, you had one application — the session lived on that server, and cookies carried a session ID back and forth. That works fine for a monolith.

Now you have six services. If the Order Service needs to know who's making a request, what are its options?

1. **Call the Auth Service on every request** — "Hey, is this token valid?" That works but creates a runtime dependency. If the Auth Service is down, nothing works. Every request now has an extra network hop.
2. **Share the session store** — Every service reads from the same Redis session store. Now your services are coupled to a shared data store, which defeats the point of independent deployability.
3. **Stateless tokens** — The Auth Service signs a token containing the user's identity. Every other service has the key to verify that signature. No callbacks, no shared state. The Auth Service could be completely offline and existing tokens still work until they expire.

Option 3 is JWT. That's why you're building this first — every subsequent service assumes tokens exist and trusts them.

---

## Subproblem 1: Project Scaffolding + User Registration

### The Logic Walkthrough

This is mostly territory you've covered. Stand up a new Spring Boot project with the PostgreSQL and Redis dependencies, plus a new one: a JWT library (most people use `jjwt` — the `io.jsonwebtoken` family of packages). Create the `users` table with id, username, password_hash, and created_at.

The registration endpoint (`POST /api/v1/auth/register`) is nearly identical to what you built before: accept a DTO, validate it, check for duplicate usernames, hash the password with BCrypt, persist the user, return a clean response.

**One thing that's different this time:** Think about what the registration response should include. In the URL shortener, registering might have redirected to a login page. Here, you have a choice — does registration automatically log the user in (return tokens immediately) or require a separate login call? Most production auth services return tokens on registration so the client doesn't have to make two calls. That's the approach to take here.

**Gotcha worth knowing:** Your `users` table is intentionally minimal — just credentials. You might be tempted to add email, display name, address, etc. Resist that. In a microservices system, user *profile* data might live in a separate profile service. The auth service owns identity and credentials, nothing more. Keep the boundary tight.

### Reading Resource

Spring Boot project initialization and JPA setup — you've done this, but if you want a clean reference for the new project structure:
**Spring Boot Reference — Getting Started:** https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html

**YouTube search:** `"Spring Boot 3 project setup PostgreSQL JPA 2024"`

### Where You'll See This Again

1. **Every service in this project** starts with the same scaffolding pattern — Spring Boot + PostgreSQL + a `Dockerfile`. You'll repeat this five more times, so get the project structure clean now.
2. **OAuth2 providers like Auth0 and Keycloak** separate identity (credentials, login) from user profile data the same way — the auth system knows as little as possible about who you are beyond "can you prove you're you."
3. **Password hashing with BCrypt** shows up in virtually every backend that stores credentials — Django, Rails, Express apps with `bcrypt.js` — the exact same algorithm and work-factor reasoning.

This subproblem matters because it establishes the foundation every other service builds on. If the users table schema is wrong or the registration flow has gaps, you'll be debugging downstream issues for weeks. It also forces you to make the first clean-boundary decision of the project: what does the auth service own, and what does it not?

---

## Subproblem 2: Understanding JWT Structure

### The Logic Walkthrough

Before you write the login endpoint, you need to understand what you're building. A JWT is three Base64-encoded chunks separated by dots:

```
eyJhbGciOiJI... . eyJ1c2VySWQiOi... . SflKxwRJSMeK...
  (header)            (payload)            (signature)
```

**The header** says what algorithm was used to sign the token. Typically `{"alg": "HS256", "typ": "JWT"}` for HMAC-SHA256 with a shared secret.

**The payload** carries claims — data about the user. You'll put `userId`, `username`, and `roles` here. Also standard claims like `iat` (issued at), `exp` (expiration time), and `sub` (subject — typically the userId). Anyone can decode the payload — it's Base64, not encrypted. So never put passwords or sensitive data here.

**The signature** is where trust comes from. The server takes the header + payload, combines them, and runs HMAC-SHA256 using a secret key that only the server knows. The result is the signature. When another service receives this token, it performs the same HMAC operation with the same secret — if the signatures match, the token hasn't been tampered with. If someone changes the userId in the payload, the signature won't match and verification fails.

**Trace through it with real values.** Say your secret is `my-256-bit-secret` and your payload is `{"userId": 42, "roles": ["USER"], "exp": 1700000000}`. The library takes the Base64 header + "." + Base64 payload, HMACs that string with your secret, and Base64-encodes the result. That's your three-part token. To verify: take the first two parts, recompute the HMAC, compare to the third part. Match? Token is legit and untampered.

**Key decision — HS256 vs RS256:**
- **HS256 (symmetric):** One secret key, used for both signing and verification. Every service that needs to verify tokens must have this secret. Simpler. Fine for this project.
- **RS256 (asymmetric):** Private key for signing (only auth service has it), public key for verification (distributed to all services). More secure in production because verifying services never hold a key that could forge tokens.

Start with HS256 — it's conceptually clearer and mechanically simpler. You can note in comments where RS256 would be the production choice.

**Gotcha:** The `exp` claim is a Unix timestamp in *seconds*, not milliseconds. Java's `System.currentTimeMillis()` returns milliseconds. If you pass milliseconds as the exp, your token will expire in the year 55,000. The JWT library probably handles this for you if you use its builder API correctly, but be aware of it.

### Reading Resource

**JWT.io Introduction:** https://jwt.io/introduction — this is the canonical explainer. Read it, then paste a token into the debugger on the same site to see the three parts decoded live.

**YouTube search:** `"JWT explained how JSON web tokens work step by step"`

### Where You'll See This Again

1. **Every OAuth2 flow** (Google Sign-In, GitHub OAuth, etc.) issues JWTs as access tokens or ID tokens. The token you get back from "Sign in with Google" has the same header.payload.signature structure.
2. **API gateways like Kong and AWS API Gateway** validate JWTs at the edge before forwarding requests to backend services — exactly the pattern your API Gateway will use in Part 7.
3. **Mobile apps** store JWTs locally (usually in secure storage) and attach them to every API call — the stateless nature of JWT is what makes offline-capable apps work without constant re-authentication.

This matters because JWT is the trust mechanism for the entire system. Every service-to-service authorization decision for the rest of this project flows through tokens this service creates. If you don't understand what's inside the token and why the signature makes it trustworthy, you'll be cargo-culting security for six services.

---

## Subproblem 3: Login Endpoint — Credential Verification + Token Issuance

### The Logic Walkthrough

The login endpoint (`POST /api/v1/auth/login`) takes a username and password, and if valid, returns two tokens. Here's the flow:

1. **Receive credentials.** DTO with username and password, validated for presence.
2. **Look up the user.** Query by username. If not found, return 401 Unauthorized. Don't say "user not found" — that leaks information about which usernames exist. Return the same generic "invalid credentials" message whether the username is wrong or the password is wrong.
3. **Verify the password.** Use BCrypt's `matches()` — you've done this. Takes the raw password and the stored hash. Returns boolean.
4. **Build the access token.** Use your JWT library's builder. Set the subject (`sub`) to the userId, add custom claims for username and roles, set `iat` to now, set `exp` to now + 15 minutes. Sign with your secret. This produces the compact token string.
5. **Build the refresh token.** This can be a JWT too, or it can be an opaque random string (like a UUID). The key difference: the refresh token gets stored server-side in Redis. Its purpose is to issue new access tokens after the access token expires. TTL: 7 days.
6. **Store the refresh token in Redis.** Key: the refresh token string (or a hash of it). Value: the userId. Set TTL to 7 days. This is how you'll revoke it later — delete the key and the refresh token becomes useless.
7. **Return both tokens** in the response body. Typically: `{"accessToken": "eyJ...", "refreshToken": "abc-def-123", "tokenType": "Bearer", "expiresIn": 900}`.

**Why two tokens instead of one?** If you had one long-lived token (say 7 days), a stolen token is dangerous for the entire 7 days and you can't revoke it without checking a blacklist on every request (which defeats stateless auth). The dual-token pattern limits the damage: a stolen access token is only useful for 15 minutes. The refresh token is longer-lived but is only ever sent to one endpoint (the refresh endpoint) and is stored in Redis where you can delete it to revoke access.

**Design decision — where does the refresh token go?** You're returning it in the JSON body. In a browser context, a more secure pattern is setting it as an HTTP-only cookie so JavaScript can't access it. For this project, the JSON body approach is fine — you're building an API, not a browser app. But know that the cookie approach exists and why.

### Reading Resource

**Auth0 — Refresh Token Rotation:** https://auth0.com/docs/secure/tokens/refresh-tokens/refresh-token-rotation — covers why refresh tokens exist, rotation, and revocation. Ignore the Auth0-specific config; focus on the concepts.

**YouTube search:** `"access token refresh token flow explained backend"`

### Where You'll See This Again

1. **Mobile apps universally use this pattern** — the iOS or Android app stores both tokens, uses the access token for API calls, and silently refreshes in the background when it expires. Users never see the 15-minute expiration because the refresh happens transparently.
2. **Slack, Discord, and most SaaS APIs** issue short-lived access tokens and longer-lived refresh tokens. If you've ever had to "re-authenticate" an app integration after a while, the refresh token likely expired or was revoked.
3. **OAuth2 spec (RFC 6749)** formalizes the access/refresh token pattern you're implementing here. You're building a simplified version of what every OAuth2 provider does internally.

This matters because token issuance is the core product of the auth service. Every other service in the system consumes what this endpoint produces. Get the claims wrong, get the TTLs wrong, or forget to store the refresh token in Redis, and you'll see cascading failures across the entire platform.

---

## Subproblem 4: JWT Validation Filter — Stateless Auth on Every Request

### The Logic Walkthrough

In the URL shortener, you wrote a filter (or interceptor) that checked for a valid session. The structure here is identical — a filter that runs before your controllers — but the mechanism is completely different. There's no session store to look up. Instead, the filter does cryptographic verification right there in memory.

Here's what the filter does on every incoming request:

1. **Check for the Authorization header.** Look for `Authorization: Bearer <token>`. If the header is missing or doesn't start with "Bearer ", skip this filter and let the request continue unauthenticated. (Some endpoints like `/auth/register` and `/auth/login` don't require auth.)
2. **Extract the token string.** Strip the "Bearer " prefix.
3. **Parse and verify.** Use your JWT library's parser, configured with the same secret you used to sign. The library does three things: decodes the header and payload, recomputes the signature and compares it, and checks the `exp` claim against the current time. If any of these fail, it throws an exception — `ExpiredJwtException`, `SignatureException`, `MalformedJwtException`, etc.
4. **Extract claims.** If verification succeeds, pull `userId` and `roles` from the payload.
5. **Set the security context.** This is the Spring Security piece. You create an `Authentication` object (typically `UsernamePasswordAuthenticationToken`) containing the userId and roles, and set it on `SecurityContextHolder.getContext()`. Downstream controllers can then access the authenticated user through Spring's standard mechanisms.
6. **Handle failures.** If the token is expired, return 401. If the signature is invalid, return 401. If the token is malformed, return 401. In all cases, don't set the security context — the request proceeds unauthenticated, and Spring Security's access rules will deny it if the endpoint requires auth.

**The critical insight compared to the URL shortener:** In the URL shortener, the filter called Redis to check if the session existed. Here, the filter does pure computation — HMAC verification and expiration check. No network call. No database hit. This is what "stateless" means: the token carries everything needed to verify it.

**Gotcha — filter ordering matters.** Your JWT filter must run before Spring Security's authorization checks. If you're using Spring Security's filter chain, register your custom filter at the right position. If Spring Security's built-in `UsernamePasswordAuthenticationFilter` tries to process the request first, it won't know what to do with a Bearer token.

**Gotcha — don't validate on auth endpoints.** You need to configure your security chain to permit `/api/v1/auth/**` without authentication. Otherwise, you can't log in because the login endpoint requires a token you don't have yet.

### Reading Resource

**Spring Security Architecture (official):** https://docs.spring.io/spring-security/reference/servlet/architecture.html — specifically the section on the filter chain. Understanding where your custom filter sits in the chain is the key to getting this right.

**YouTube search:** `"Spring Security JWT filter chain custom OncePerRequestFilter 2024"`

### Where You'll See This Again

1. **Every microservice you build in this project** will either contain this same filter or rely on the API Gateway to perform this validation. The Product Catalog, Order, Review, and Payment services all need to know who's making the request.
2. **AWS API Gateway + Lambda** uses the same pattern — a JWT authorizer validates the token at the gateway level, extracts claims, and passes them as context to the Lambda function. No session store involved.
3. **Middleware in Express.js (Node) and Middleware in ASP.NET** follow the exact same structural pattern — a function that intercepts requests, verifies the token, and attaches user info to the request context before the handler runs.

This matters because the validation filter is the contract between auth and every other service. If the filter is lenient (doesn't properly check expiration or signature), the security of the entire system is compromised. If it's too aggressive (rejects valid tokens due to clock skew), users get randomly logged out. Getting this right once means every downstream service can copy it and trust it.

---

## Subproblem 5: Refresh Token Flow + Redis Revocation

### The Logic Walkthrough

The refresh endpoint (`POST /api/v1/auth/refresh`) is how the client gets a new access token without re-entering credentials. Here's the flow:

1. **Receive the refresh token.** The client sends it in the request body (not the Authorization header — the access token is expired, so the Authorization header is useless at this point).
2. **Look it up in Redis.** Query Redis with the refresh token as the key. If it's not there, the token has been revoked or has expired (Redis TTL handles expiration). Return 401.
3. **Extract the userId** from the Redis value.
4. **Delete the old refresh token from Redis.** This is refresh token rotation — each refresh token is single-use. Once used, it's gone.
5. **Issue a new access token** (same process as login — build JWT with claims, sign it).
6. **Issue a new refresh token.** Generate a new one, store it in Redis with the same TTL pattern. Return both tokens to the client.
7. **Return the new token pair.**

**Why rotate refresh tokens (step 4)?** If a refresh token is stolen and the attacker uses it, the legitimate user's next refresh attempt will fail (because the token was already consumed). This is a detection signal. Without rotation, a stolen refresh token could be used silently for 7 days.

**The revocation story:** If a user logs out, you delete their refresh token from Redis. Their current access token still works for up to 15 minutes (this is the stateless tradeoff — you can't "un-sign" a JWT). If you need instant revocation, you'd add a Redis-backed blacklist that the validation filter checks, but that reintroduces statefulness. For this project, accept the 15-minute window. Note the tradeoff in comments.

**Gotcha — race conditions on rotation.** If the client sends two refresh requests simultaneously (maybe a race condition in a mobile app), the first request consumes the token and the second fails. The client sees an unexpected 401. Production systems handle this by allowing a short grace period for the old token after rotation, or by using token families to detect concurrent use. For this project, simple rotation is sufficient — just be aware the edge case exists.

**Redis key design.** Keep it simple: `refresh:<token_value>` → `<userId>`. TTL: 7 days (same as the token's logical lifetime). When you need to revoke all sessions for a user (say, a password change), you'd need to find all refresh tokens for that user. One approach: also maintain a Redis set `user_tokens:<userId>` containing all active refresh token keys, so you can iterate and delete. This is optional for now but worth thinking about.

### Reading Resource

**Redis Commands — SET with EX and GET:** https://redis.io/docs/latest/commands/set/ — you've used Redis before, but review the options around TTL (`EX` for seconds, `PX` for milliseconds) and conditional sets (`NX` — only if key doesn't exist) since the NX flag is useful for idempotent token storage.

**YouTube search:** `"refresh token rotation revocation Redis backend security"`

### Where You'll See This Again

1. **OAuth2 refresh token rotation** is now a recommended security practice by the IETF (RFC 6749 + Security BCP). What you're implementing is a simplified version of the industry standard.
2. **The Payment Service's idempotency keys (Part 4)** use the same Redis pattern — store a key with a TTL, check before processing, delete or expire when done. You're learning the general pattern of "Redis as a short-lived state store for things that aren't entities" which pays off two services later.
3. **Session invalidation in systems like GitHub and Google** — when you click "sign out of all devices," the backend revokes all refresh tokens (or session tokens). The implementation is the same deletion-from-a-store approach.

This matters because the refresh flow is what makes your auth usable in practice. Without it, users would have to re-enter credentials every 15 minutes. With it, the user experience is seamless and the security model is sound. It's also the first real coordination between a REST endpoint and Redis that has *security* implications — getting the ordering wrong (issuing a new token before deleting the old one, for example) creates a subtle vulnerability.

---

## Subproblem 6: Security Configuration + Exception Handling + Testing

### The Logic Walkthrough

This is the wiring subproblem — pulling the pieces together into a coherent Spring Security configuration and making sure errors are clean.

**Security Configuration:**

You need a `SecurityFilterChain` bean that defines:
- Which endpoints are public: `/api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/refresh`
- Which endpoints require authentication: everything else
- Where your JWT filter sits in the chain: before `UsernamePasswordAuthenticationFilter`
- CSRF disabled (you're building a stateless API, not serving HTML forms — CSRF protection via tokens doesn't apply when there are no cookies carrying auth in browser requests... and you're returning tokens in response bodies)
- Session management set to STATELESS (tells Spring Security not to create HTTP sessions)

**Exception handling:**

You already built a global exception handler with `@RestControllerAdvice` in the URL shortener. Extend it here with auth-specific exceptions:
- `401 Unauthorized` — bad credentials, expired token, invalid token, revoked refresh token
- `409 Conflict` — duplicate username on registration
- Make sure your JWT filter exceptions get translated into proper JSON error responses, not Spring's default HTML error page. The filter runs outside the normal Spring MVC exception handling flow, so your `@RestControllerAdvice` won't catch exceptions thrown in the filter. You'll need to handle them directly in the filter (catch the exception, write the JSON error response to the `HttpServletResponse`).

**That filter exception gotcha deserves emphasis.** This trips up a lot of people. Your `@RestControllerAdvice` catches exceptions thrown by controllers. But the JWT filter runs *before* the request reaches a controller. If the filter throws, you get a raw 500 or Spring's whitelabel error page. Handle it in the filter itself, or delegate to a `HandlerExceptionResolver` from within the filter.

**Testing strategy:**

- **Unit tests:** Test your JWT utility class in isolation — can it create a token? Can it parse one? Does it reject an expired token? Does it reject a tampered token? Create a token, manually alter one character in the payload, and verify that parsing fails. These tests don't need Spring context.
- **Repository integration tests:** Testcontainers + `@DataJpaTest` for the users table. You know this pattern.
- **Controller integration tests:** `@SpringBootTest` + `MockMvc` (or `WebTestClient`). Test the full flows:
  - Register → get tokens → use access token on a protected endpoint → succeed
  - Hit a protected endpoint with no token → 401
  - Hit a protected endpoint with an expired token → 401
  - Register → login → refresh → use new access token → succeed
  - Refresh with a revoked token → 401
- **Redis integration tests:** Testcontainers Redis. Verify refresh token storage, retrieval, deletion, and TTL behavior.

### Reading Resource

**Spring Security reference — Servlet Security: The Big Picture:** https://docs.spring.io/spring-security/reference/servlet/architecture.html (yes, the same link as subproblem 4 — the filter chain architecture section is essential for understanding where exceptions get caught and where they don't).

**YouTube search:** `"Spring Boot JWT authentication testing MockMvc integration test"`

### Where You'll See This Again

1. **Every service in this system** will have a security configuration that validates JWTs. Services 2–5 are simpler (they only validate, never issue), but the `SecurityFilterChain` pattern is identical.
2. **Filter-level exception handling** is a pattern you'll revisit in the API Gateway (Part 7), where routing failures and auth failures at the gateway level also live outside the MVC exception handling flow.
3. **Integration tests with Testcontainers** for Redis and PostgreSQL become standard practice across all six services. The test patterns you establish here get copied forward.

This matters because the security configuration is where all the subproblems converge into a running system. A misconfigured filter chain means either everything is open (security hole) or everything is blocked (nothing works). And the exception handling gotcha in filters is the kind of thing that wastes an afternoon if you don't know about it upfront — your tests will show you a 500 error with no useful message and you'll be digging through stack traces wondering why your `@RestControllerAdvice` isn't catching anything.

---

## How This Connects to What's Next

When you finish the Auth Service, you'll have:
- A running service that issues JWTs
- A validation filter you understand deeply
- Refresh token management in Redis
- A Docker-ready Spring Boot app with Testcontainers tests

**Part 2 (Product Catalog)** will be the first service that *consumes* these tokens. You'll copy the JWT validation filter (or extract it into a shared library), stand up a new service with its own database, and add two things you haven't seen: GraphQL for client-facing queries and a gRPC server for internal communication from the Order Service.

The Auth Service doesn't exist in isolation — it exists so that when the Order Service receives `POST /api/v1/orders`, it can extract `userId: 42` from the token, call the Product Catalog via gRPC to check stock, call the Payment Service via gRPC to charge the user, and publish an event to RabbitMQ — all knowing *who* the user is without a single session lookup.

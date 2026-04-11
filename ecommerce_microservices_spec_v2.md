# Project 2: E-Commerce Order System (Microservices)
**Stack:** Java, Spring Boot, PostgreSQL, Redis, JWT, GraphQL, gRPC, RabbitMQ, Docker Compose  
**Prerequisite:** Completed URL Shortener project (Parts 1–6) covering REST APIs, JPA, Redis caching, session auth, soft delete, Testcontainers  
**Goal:** Build a production-style e-commerce backend as a microservices system. Each service is independently deployable with its own database. This project reinforces fundamentals from the URL shortener while introducing inter-service communication patterns, stateless auth, async messaging, and multiple API paradigms.

---

## Architecture Overview

Six services, each with its own PostgreSQL database, communicating via a mix of synchronous and asynchronous patterns:

```
                         ┌──────────────┐
                         │  API Gateway  │
                         └──────┬───────┘
                                │
     ┌──────────┬───────┬───────┼───────┬──────────┬─────────────┐
     │          │       │       │       │          │             │
┌────▼─────┐ ┌─▼──────┐│ ┌─────▼─────┐ │  ┌───────▼───┐ ┌──────▼─────┐
│  Auth    │ │Product  ││ │  Payment  │ │  │  Review   │ │Notification│
│ Service  │ │Catalog  ││ │ Service   │ │  │  Service  │ │  Service   │
│  (JWT)   │ │(GraphQL)││ │(gRPC +    │ │  │(Pagination│ │ (RabbitMQ  │
│          │ │         ││ │rate limit)│ │  │+ queries) │ │  consumer) │
└──────────┘ └─────────┘│ └───────────┘ │  └───────────┘ └────────────┘
                   ┌────▼────┐          │
                   │  Order  │──────────┘
                   │ Service │
                   │ (gRPC)  │
                   └─────────┘
```

**Communication patterns:**
- Client → API Gateway → Services: REST (auth, orders, reviews), GraphQL (products)
- Order Service → Product Catalog: gRPC (synchronous, internal)
- Order Service → Payment Service: gRPC (synchronous, internal)
- Order Service → Notification Service: RabbitMQ (asynchronous, event-driven)
- Review Service → Product Catalog: gRPC (synchronous, internal — aggregate rating lookups)

---

## Service Breakdown

### 1. Auth Service
**Tech:** REST, JWT, BCrypt, PostgreSQL, Redis  
**Purpose:** User registration, login, and token management.

**What's different from the URL shortener:** The URL shortener used Redis-backed sessions with cookies — a stateful approach tied to one application. Here, JWT is the right tool because multiple independent services need to verify identity without calling back to the auth service. The auth service issues signed tokens; every other service validates them locally using the public key or shared secret.

**Core functionality:**
- `POST /api/v1/auth/register` — register with username/password, BCrypt hashing
- `POST /api/v1/auth/login` — verify credentials, return JWT access token + refresh token
- `POST /api/v1/auth/refresh` — issue new access token from valid refresh token
- JWT contains userId and roles in the payload, signed with a secret
- Access token TTL: 15 minutes. Refresh token TTL: 7 days, stored in Redis for revocation
- Every other service validates the JWT from the `Authorization: Bearer` header without calling the auth service

**Key concepts to learn:**
- JWT structure (header.payload.signature), signing, and verification
- Access vs refresh token pattern and why short-lived access tokens matter
- Stateless auth tradeoffs vs the session approach from the URL shortener (no server-side revocation of access tokens without Redis blacklisting)
- Token refresh flow

**Database:** `users` table (id, username, password_hash, created_at)

---

### 2. Product Catalog Service
**Tech:** GraphQL (Spring for GraphQL), PostgreSQL, Redis  
**Purpose:** Manage products with flexible querying for different client needs.

**Why GraphQL here:** A product catalog has different consumers — a listing page needs name/price/image, a detail page needs full description and reviews, a mobile client wants minimal data. REST would require multiple endpoints or over-fetching. GraphQL lets the client request exactly the fields it needs in a single query.

**Core functionality:**
- GraphQL queries: `products(category, priceRange, page)`, `product(id)`, `productsByIds(ids)`
- GraphQL mutations: `createProduct(input)`, `updateProduct(id, input)`, `deleteProduct(id)` — admin only
- Schema-first design with `.graphqls` schema files
- Redis caching on product lookups (cache-aside, same pattern as URL shortener)
- Exposes a gRPC server endpoint for internal calls from the Order Service (product availability and price verification)

**Key concepts to learn:**
- GraphQL schema definition language (SDL)
- Queries vs mutations vs subscriptions
- Resolver pattern — how GraphQL maps queries to Java methods
- N+1 problem and DataLoader batching
- How GraphQL and REST coexist in the same service

**Database:** `products` table (id, name, description, price, stock_quantity, category, image_url, created_at, updated_at)

---

### 3. Order Service
**Tech:** REST (client-facing), gRPC client (internal calls), RabbitMQ producer, PostgreSQL  
**Purpose:** Handle order placement, orchestrating product verification and payment processing.

**Why gRPC here:** When a user places an order, the order service must verify product availability (catalog service) and process payment (payment service). These are internal service-to-service calls — no browser, no need for human-readable JSON. gRPC with Protocol Buffers provides typed contracts, generated client/server stubs, and better performance than REST for high-frequency internal communication.

**Core functionality:**
- `POST /api/v1/orders` — place an order (authenticated via JWT)
  1. Validate the order request
  2. Call Product Catalog via gRPC to verify availability and current prices
  3. Call Payment Service via gRPC to process payment
  4. Save order to database
  5. Publish `OrderPlaced` event to RabbitMQ
  6. Return order confirmation
- `GET /api/v1/orders` — list user's orders (JWT-scoped)
- `GET /api/v1/orders/{id}` — order detail (ownership check, same pattern as URL shortener)
- Order state machine: PENDING → CONFIRMED → PAID → SHIPPED → DELIVERED / CANCELLED

**Key concepts to learn:**
- Protocol Buffers (`.proto` files) — contract-first, code generation
- gRPC unary calls (request-response, like REST but binary)
- Orchestration pattern — one service coordinates a multi-step workflow
- Idempotency — what happens if the payment call succeeds but saving the order fails?
- RabbitMQ producer pattern — publish-and-forget async events

**Database:** `orders` table (id, user_id, status, total_price, created_at, updated_at), `order_items` table (id, order_id, product_id, quantity, unit_price)

---

### 4. Payment Service
**Tech:** gRPC server (receives internal calls), REST (payment history), Redis (rate limiting), PostgreSQL  
**Purpose:** Process payments with rate limiting and idempotency guarantees. Exposes a modern gRPC interface for internal callers and a REST API for payment history queries.

**Why rate limiting and idempotency here:** Payments are the highest-stakes operation in the system. A bug or misbehaving client that submits 50 payment requests per second can cause real financial damage. Rate limiting protects the service from abuse — both intentional (attacks) and accidental (retry loops). Idempotency keys prevent double charges when the network is unreliable: if the Order Service sends a payment request, gets a timeout, and retries, the Payment Service must recognize the duplicate and return the original result instead of charging twice.

**What replaced SOAP:** The original spec had this service wrapping a SOAP-based legacy payment processor. That's been removed. Instead, the Payment Service calls a mock payment processor internally (a simple class that simulates success/failure/delay) — the focus shifts from protocol translation to operational safety patterns that apply regardless of what the downstream processor looks like.

**Core functionality:**
- gRPC endpoint: `ProcessPayment(userId, orderId, amount, idempotencyKey)` → returns success/failure with transaction ID
- `GET /api/v1/payments?orderId=&status=` — query payment history (authenticated, ownership-scoped)
- `GET /api/v1/payments/{id}` — payment detail
- Rate limiting on the gRPC `ProcessPayment` endpoint:
  - Per-user rate limit (e.g., 5 payment requests per minute per userId)
  - Implemented with Redis sliding window or token bucket
  - Returns gRPC `RESOURCE_EXHAUSTED` status when exceeded
- Idempotency key handling:
  - Client sends an `idempotency_key` with each payment request
  - Before processing, check Redis/DB: has this key been seen?
  - If yes, return the stored result without reprocessing
  - If no, process the payment, store the result keyed by `idempotency_key`
  - Key TTL: 24 hours (configurable)
- Records all payment attempts (including rate-limited rejections) for audit trail
- Mock processor simulates: instant success, delayed success (to test timeout handling), random failures, insufficient funds

**Key concepts to learn:**
- gRPC server implementation (the catalog service also has one — by the second time, the pattern is familiar)
- Rate limiting algorithms: sliding window counter vs token bucket, why Redis is the right backing store for distributed rate limiting
- Idempotency key pattern — how to make non-idempotent operations safe to retry
- The relationship between rate limiting and idempotency (rate limiting prevents floods, idempotency prevents duplicates — complementary protections)
- gRPC error status codes and how to communicate rate limiting / idempotency outcomes through gRPC's status model

**Database:** `payments` table (id, order_id, user_id, amount, status, idempotency_key, processor_transaction_id, failure_reason, created_at), `rate_limit_events` table (optional — for audit trail of rejected requests)

---

### 5. Review Service
**Tech:** REST, PostgreSQL (complex queries), Redis (caching aggregates), gRPC client (product verification)  
**Purpose:** User reviews and ratings for products, with rich filtering, sorting, and pagination — the service that pushes SQL and query design the hardest.

**Why a separate Review Service:** Reviews belong to both a user and a product, but they're a different domain than the product catalog. The catalog team shouldn't need to redeploy when you change how reviews are sorted or filtered. More importantly for your learning: reviews are the natural domain for complex query patterns. A product with 10,000 reviews needs cursor-based pagination. A user browsing reviews wants to filter by star rating, sort by date or helpfulness, and see aggregate distributions — all of which translate to parameterized queries, composite indexes, and aggregation SQL.

**Core functionality:**

*Write operations:*
- `POST /api/v1/products/{productId}/reviews` — submit a review (authenticated)
  - Calls Product Catalog via gRPC to verify the product exists
  - One review per user per product (enforced at DB level with unique constraint on `user_id, product_id`)
  - Request body: `rating` (1–5), `title`, `body`
- `PUT /api/v1/products/{productId}/reviews/{id}` — edit own review (ownership check)
- `DELETE /api/v1/products/{productId}/reviews/{id}` — soft delete own review

*Read operations (where the SQL complexity lives):*
- `GET /api/v1/products/{productId}/reviews` — paginated reviews for a product
  - **Cursor-based pagination** (not offset-based): client passes `?cursor=<encoded_value>&limit=20`
  - **Sort options:** `sort=newest` (default), `sort=oldest`, `sort=highest_rated`, `sort=lowest_rated`, `sort=most_helpful`
  - **Filter options:** `?minRating=4`, `?maxRating=3`, `?hasBody=true` (only reviews with text)
  - Sorting + filtering + cursor pagination interact in non-trivial ways — this is the core challenge
- `GET /api/v1/products/{productId}/reviews/summary` — aggregate stats for a product
  - Average rating, total count, rating distribution (how many 1-star, 2-star, etc.)
  - This is a GROUP BY + COUNT query, cached in Redis with invalidation on new reviews
- `GET /api/v1/users/me/reviews` — all reviews by the authenticated user (paginated)
- `GET /api/v1/products/{productId}/reviews/{id}` — single review detail

*Helpfulness voting:*
- `POST /api/v1/reviews/{id}/helpful` — mark a review as helpful (authenticated, one vote per user per review)
- `DELETE /api/v1/reviews/{id}/helpful` — remove helpful vote
- The `helpful_count` on each review is what drives the `sort=most_helpful` option — this is a denormalized counter that must stay in sync with the votes table

**Key concepts to learn:**

*Cursor-based pagination:*
- Why offset pagination breaks at scale (skipping 10,000 rows is expensive) and why cursor-based is O(1) regardless of page depth
- Encoding cursor values (typically the sort column's value + the row ID for tiebreaking)
- How cursors interact with different sort orders — the `WHERE` clause changes depending on sort direction
- Keyset pagination in JPA: using `WHERE (created_at, id) < (:cursorDate, :cursorId)` instead of `OFFSET`

*Parameterized and dynamic queries:*
- Building queries with optional filters: if `minRating` is provided, add `AND rating >= :minRating`; if not, omit the clause entirely
- JPA Specification pattern (`Specification<Review>`) for composable query predicates
- Alternatively, JPQL with conditional fragments or native queries for complex cases
- When JPA's abstraction helps and when it gets in the way (aggregate queries are easier in native SQL)

*Composite indexes and query performance:*
- The `product_id + created_at + id` index for paginated queries sorted by newest
- The `product_id + rating` index for filtered queries
- The `user_id + product_id` unique index for the one-review-per-user constraint
- Understanding `EXPLAIN ANALYZE` output to verify your indexes are being used

*Aggregation:*
- `SELECT rating, COUNT(*) FROM reviews WHERE product_id = ? GROUP BY rating` for rating distribution
- Caching aggregates in Redis and invalidating on writes — same cache-aside pattern but for computed data, not raw entities
- The tradeoff between computing aggregates on-read (always fresh, slower) vs maintaining denormalized counters on-write (fast reads, complexity on writes)

**Database:**
- `reviews` table (id, product_id, user_id, rating, title, body, helpful_count, is_deleted, created_at, updated_at)
- `review_helpful_votes` table (id, review_id, user_id, created_at) — unique constraint on `review_id, user_id`
- **Indexes:**
  - `idx_reviews_product_created` on (product_id, created_at DESC, id DESC) — default pagination
  - `idx_reviews_product_rating` on (product_id, rating) — rating filter queries
  - `idx_reviews_product_helpful` on (product_id, helpful_count DESC, id DESC) — most helpful sort
  - `idx_reviews_user` on (user_id, created_at DESC) — user's own reviews
  - `uq_reviews_user_product` unique on (user_id, product_id) — one review per user per product

---

### 6. Notification Service
**Tech:** RabbitMQ consumer, PostgreSQL  
**Purpose:** Consume order events asynchronously and send notifications (email stubs).

**Why RabbitMQ here:** When an order is placed, the user needs a confirmation. But the order service shouldn't wait for the email to send before responding — that would slow down the order flow and create a coupling between ordering and notification. A message queue decouples them: the order service publishes an event and moves on. The notification service picks it up whenever it's ready. This is the first async communication pattern in the curriculum.

**Core functionality:**
- Listens to `order.placed` queue for `OrderPlaced` events
- Listens to `order.status.changed` queue for status updates
- Logs notification content (email stub — no actual email sending needed)
- Stores notification history in its own database
- Handles message acknowledgment, retry on failure, dead letter queue for poison messages

**Key concepts to learn:**
- RabbitMQ exchanges, queues, and bindings
- Message acknowledgment (manual ack vs auto ack)
- Dead letter queues — what happens when a message fails repeatedly
- Idempotent message processing — the same message might be delivered twice
- Async vs sync communication tradeoffs

**Database:** `notifications` table (id, user_id, order_id, type, content, status, created_at)

---

## Cross-Cutting Concerns

### API Gateway
A simple Spring Cloud Gateway (or a manually configured reverse proxy) that routes external requests to the appropriate service. Handles JWT validation at the edge so individual services can trust the token has been verified. This is optional but recommended — it centralizes auth checking and provides a single entry point.

### Database-Per-Service
Each service has its own PostgreSQL database (or schema). Services never share databases or read each other's tables. If the order service needs product data, it calls the product service — it doesn't query the products table directly. This is the core microservices data isolation principle.

### Docker Compose
All six services, their databases, Redis, and RabbitMQ run in Docker Compose for local development. Each service gets its own `Dockerfile`. The compose file wires the network so services can reach each other by hostname.

### Testing Strategy
Same approach as the URL shortener, applied per service:
- Unit tests with Mockito for service layer logic
- Repository integration tests with `@DataJpaTest` + Testcontainers
- Controller/endpoint integration tests with `@SpringBootTest`
- gRPC endpoint tests using gRPC's in-process server
- RabbitMQ consumer tests using Testcontainers RabbitMQ module
- GraphQL tests using Spring's `GraphQlTester`
- **Review Service query tests:** Seed databases with large datasets (1000+ reviews) and verify pagination, sorting, and filtering correctness. Use `EXPLAIN ANALYZE` assertions to verify index usage.

---

## Suggested Build Order

Build one service at a time, each one introducing a new technology while reinforcing fundamentals:

**Part 1: Auth Service (JWT)**
Reinforces: user registration, BCrypt, REST, PostgreSQL, Redis, validation, exception handling
New: JWT signing/verification, access/refresh tokens, stateless auth

**Part 2: Product Catalog Service (GraphQL + gRPC server)**
Reinforces: PostgreSQL, Redis caching (cache-aside), validation, DTOs
New: GraphQL schema and resolvers, gRPC server with Protocol Buffers

**Part 3: Order Service (gRPC client + RabbitMQ producer)**
Reinforces: REST, PostgreSQL, JPA relationships, ownership scoping, JWT validation
New: gRPC client calls, RabbitMQ message publishing, orchestration pattern

**Part 4: Payment Service (gRPC server + rate limiting + idempotency)**
Reinforces: gRPC server (second time), PostgreSQL, Redis
New: Rate limiting algorithms (sliding window / token bucket), idempotency key pattern, gRPC error status mapping

**Part 5: Review Service (complex queries + cursor pagination)**
Reinforces: REST, PostgreSQL, JPA, Redis caching, JWT validation, ownership scoping, soft delete
New: Cursor-based pagination, JPA Specifications for dynamic queries, composite indexes, aggregation queries, denormalized counters, `EXPLAIN ANALYZE`

**Part 6: Notification Service (RabbitMQ consumer)**
Reinforces: PostgreSQL, Spring component model
New: RabbitMQ consumer, message acknowledgment, dead letter queues, async processing

**Part 7: Docker Compose & Integration**
Reinforces: Docker Compose, Testcontainers
New: Multi-service orchestration, API gateway routing, end-to-end flows across services

---

## Technology Summary

| Technology | Where It Appears | Why It's Used |
|---|---|---|
| JWT | Auth Service → all services | Stateless auth across independent services |
| GraphQL | Product Catalog (client-facing) | Flexible queries for different consumers |
| gRPC + Protobuf | Order ↔ Catalog, Order ↔ Payment, Review → Catalog | Typed internal service-to-service communication |
| RabbitMQ | Order → Notification | Async event-driven communication |
| Redis | Auth (refresh tokens), Catalog (caching), Payment (rate limiting + idempotency), Review (aggregate caching) | Multiple patterns: caching, rate limiting, idempotency stores |
| PostgreSQL | Every service (own database) | Database-per-service isolation |
| Cursor Pagination | Review Service | Efficient pagination over large datasets |
| Rate Limiting | Payment Service | Protecting sensitive operations from abuse |
| Docker Compose | All services | Local multi-service orchestration |
| Testcontainers | All services | Integration tests against real infrastructure |

---

## What Was Removed and Why

**SOAP integration** was removed from the Payment Service. SOAP is worth understanding conceptually (XML-based, WSDL contracts, WS-Security) but building a SOAP client and mock server is better done in isolation if curiosity demands it. The time is better spent on rate limiting, idempotency patterns, and complex query design — skills that apply to nearly every production backend.

---

## Student Context

Carlos is building this as the second project in a structured backend engineering curriculum. He completed the URL Shortener (Parts 1–6) which covered Spring Boot, JPA, REST, Redis caching, session auth, soft delete, and Testcontainers. Key patterns he's comfortable with: cache-aside, filter-based auth, atomic DB operations, DTO validation, global exception handling, repository/service/controller layering.

**Teaching approach that works for Carlos:**
- Socratic method for architecture and design decisions — ask guiding questions, let him reason through tradeoffs before providing answers
- Adjacent problem pattern for new syntax and annotations — provide an 85–95% similar working example in a parallel domain and let him adapt it
- Concrete trace-throughs with specific values when explaining new concepts
- Session wind-down reviews summarizing what was built and why
- Direct and honest feedback — Carlos pushes back when he disagrees and expects the same in return
- Don't over-explain what he already knows from the URL shortener — reference the prior project and build on it

**What he explicitly excluded:** WebSockets (saved for a separate project), SOAP (removed — will explore in isolation if needed).

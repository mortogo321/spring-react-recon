# Settlement Reconciliation

Every card acquirer sends a settlement file. Somebody has to prove it agrees with the ledger, and
work the rows where it doesn't. This is that system, end to end: a nightly batch job that reads the
acquirer feed out of a legacy Oracle schema, reconciles it against the ledger in MySQL, and a
console where an operator investigates each break and an approver — a different person — decides it.

It exists as a worked example of a migration that most payment estates are somewhere inside: the
system of record is Oracle and nobody is going to move it this year, the new work is Spring Boot and
JPA, and the two have to run side by side without the new half inheriting the old half's shape.

```
  Oracle (legacy, read-only)          MySQL (owned by this system)
  settlement feed, merchants          runs · exceptions · ledger · outbox
            │                                        ▲
            │  MyBatis                          JPA  │
            └──────────►  Spring Batch job  ─────────┘
                          partitioned by merchant
                                    │
                          REST  ────┴────►  React console
```

---

## Run it

```bash
docker compose up --build          # first run pulls Oracle Free; allow a few minutes
open http://localhost:5173
```

Four containers: Oracle with the legacy schema, MySQL with the application's own, the API, and nginx
serving the console and proxying `/api` to the API so the browser sees a single origin. Nothing
needs a `.env` file — every setting has a working default. Copy `.env.example` to `.env` to change
ports or credentials.

Sign in as any of three demo identities, each of which can do something the others cannot:

| User       | Password   | Can                                        |
|------------|------------|--------------------------------------------|
| `admin`    | `admin`    | launch, stop, restart and recover runs      |
| `operator` | `operator` | investigate a break and propose a resolution |
| `approver` | `approver` | approve or reject what an operator proposed |

Then: **Runs → Launch run → 2026-08-20**. The job partitions across six merchants, reconciles 100
settlement rows against the ledger, and raises 13 breaks across all five break classes — an 86.73%
match rate and ฿29,115.80 of open exposure. Pick a break in the queue, take ownership, propose a
write-off, sign out, sign back in as `approver`, and decide it.

Try approving your own proposal. You can't — and the console does not hide the button, it lets the
API answer and shows you why. That is the one control this whole system exists to enforce.

### Without Docker

The `local` profile swaps both databases for in-memory H2, seeded with the same demo data, so the
whole system runs from a checkout with no containers at all:

```bash
./gradlew :backend:recon-api:bootRun --args='--spring.profiles.active=local'
cd frontend && bun install && bun run dev        # http://localhost:5173
```

API docs are at `/swagger-ui.html`, health at `/actuator/health/readiness`.

---

## What reconciliation means here

Two records match when their **match key** agrees — merchant, business date and external reference —
and the amounts agree to within the run's tolerance. Everything else is a break, and which kind it is
determines who cares:

| Class                   | Severity  | Meaning                                                   |
|-------------------------|-----------|-----------------------------------------------------------|
| `AMOUNT_MISMATCH`       | critical  | both sides present, the money differs by more than tolerance |
| `MISSING_IN_LEDGER`     | critical  | the acquirer settled it and nothing was posted             |
| `MISSING_IN_SETTLEMENT` | warning   | posted, but the acquirer never settled it                  |
| `DUPLICATE_SETTLEMENT`  | critical  | the same reference settled twice                           |
| `CURRENCY_MISMATCH`     | critical  | the two sides disagree on the currency itself              |

Tolerance is configuration rather than code, because a scheme changes its rounding and somebody has
to widen the allowance today, not next release. Allowance is `max(absolute, amount × bps / 10000)`,
and a run picks a named profile:

| Profile   | Absolute | Basis points |
|-----------|----------|--------------|
| `strict`  | 0.00     | 0            |
| `default` | 0.50     | 10           |
| `relaxed` | 50.00    | 100          |

The same demo day reconciles differently under each, which is the point of making it a run
parameter: `2026-08-20` raises 13 breaks at 86.73% on `default`, and 15 at 84.69% on `strict`.

Money is never a `double`. It is a `Money` value object over `BigDecimal` with an explicit currency,
and an ArchUnit rule fails the build if a floating-point field ever appears in a class whose name
suggests it holds money.

---

## How it is put together

Five Gradle modules, and the dependency direction is the design:

```
domain  ←  core · legacy  ←  batch  ←  api
```

| Module          | Holds                                                                    |
|-----------------|--------------------------------------------------------------------------|
| `recon-domain`  | matching, tolerance, money. No Spring, no JPA, no annotations at all.      |
| `recon-legacy`  | the Oracle side: MyBatis mappers, XML SQL, type handlers, a read-only pool |
| `recon-core`    | the MySQL side: JPA entities, repositories, workflow services, the outbox  |
| `recon-batch`   | the Spring Batch job — partitioner, reader, processor, writer, tasklets    |
| `recon-api`     | REST controllers, security, the composition root                           |

Gradle enforces this between modules; ArchUnit states it again inside them, along with the rules
Gradle cannot see — the domain stays framework-free, controllers never touch a repository directly,
MyBatis mappers are reachable only through a gateway, no field injection, no `java.util.Date`, no
`System.out`.

The domain being framework-free is what makes it worth having. The matching logic is 37 tests that
run in milliseconds with no application context, at 93% line coverage, and it cannot quietly acquire
a database connection.

### The two databases

They are wired as genuinely separate `DataSource` beans, not one connection with two schemas. Oracle
is reached through MyBatis with hand-written SQL in XML, connected as a read-only account that cannot
write even if the code tried — that is what a legacy system of record actually looks like. MySQL is
this system's own, owned outright, with JPA and Flyway migrations.

### The job

`ReconciliationJobConfig` is a partitioned, restartable, chunk-oriented job:

- **Partitioned by merchant.** `MerchantShardPartitioner` packs merchants into shards by row count
  rather than by name, so one merchant with 41 rows and five with fewer do not leave five workers idle.
- **Restartable.** A run that fails part-way resumes from the failed step, and the API exposes
  restart, stop, abandon and recover so an operator can act on it without shell access.
- **Skip and retry.** A poison row is skipped up to a limit and reported, with backoff on retry: one
  bad record must not fail a nightly run at 3 a.m.
- **Idempotent by day.** Re-triggering a date that already completed is a no-op, not a second run.
- **A decider**, not a flag, decides whether a date needs reconciling at all.
- **A transactional outbox** rather than an event published inside the same transaction, so a
  notification cannot be emitted for a run that then rolls back.

Every step reports read, filtered, written, skipped, committed and rolled-back counts, and the
console shows them per partition — which is how you see that a shard is skipping rather than working.

### The API

Stateless JWT resource server. Authorisation is enforced twice on purpose: coarsely by URL, so an
auditor can read the rules in one place, and precisely at the service layer with `@PreAuthorize`, so
the rule still holds when someone adds a second controller onto the same service next quarter.

Also: RFC 9457 problem responses, an `Idempotency-Key` filter that stops the double-click without
being spent by a rejected request, a correlation id echoed on every response and threaded through
the logs, optimistic locking on every workflow transition, keyset pagination alongside offset
pagination for the exception queue.

### The console

React with MUI, Redux Toolkit for server state, and the URL as the source of truth for filter and
page state, so an operator can send a colleague a link to exactly the queue they are looking at.
Vendor chunks are split by change cadence — the data grid and the charts move on their own release
schedule and shouldn't invalidate a megabyte of cache when a page changes.

Role checks in the console are a courtesy, not a control. The server decides; the console shows what
it decided.

---

## Tests

```bash
./gradlew build                      # spotless, ArchUnit, unit, integration, coverage floor
./gradlew :backend:recon-api:integrationTest   # against real Oracle and MySQL (needs Docker)
cd frontend && bun run test          # component and reducer tests
cd frontend && bunx playwright test  # the journey, in a browser, against a running API
```

Tests that need a Docker daemon are tagged `docker` and excluded from `test`, so a laptop without
one still gets a green `build`.

| Layer                     | What it proves                                                  |
|---------------------------|-----------------------------------------------------------------|
| 37 domain unit tests      | the matching rules, tolerance edges, money arithmetic            |
| 8 ArchUnit rules          | the dependency direction and the conventions Gradle can't see    |
| 9 API contract tests      | status codes, problem shapes, idempotency, roles on the wire     |
| 5 `@SpringBatchTest` tests| the job's partitioning, restart and skip behaviour               |
| 2 Testcontainers tests    | the same reconciliation on real Oracle and MySQL, and the grants |
| 28 console tests          | reducers, capability gating, the exception drawer                |
| 6 Playwright specs        | sign-in, launch, investigate, approve — with two real sessions   |

The e2e suite is the only place segregation of duties is proved across every layer at once. Each
layer enforces it in its own way — a `@PreAuthorize` expression, a service check, a disabled button —
and the only way to know all three agree is to walk one break through them with two different users.

The H2 legacy database runs in Oracle compatibility mode against the same DDL and the same mappers,
which keeps the fast tests honest about the dialect. What it cannot prove is that the hand-written
SQL parses on a real Oracle, that the type handlers read Oracle's own `NUMBER` and `DATE`
representations, or that the read-only account can reach the owner's tables through its synonyms
and still cannot write to them. That is what the Testcontainers pair is for, seeded by the very
script compose mounts — so the account split under test is the arrangement that ships.

CI runs the backend and the console in parallel, the real-database job alongside them, and the e2e
suite against a freshly built API on the `local` profile.

---

## Configuration

Everything reads an environment variable with a demo-safe default, so the same image runs locally and
in a deployment without a rebuild. `.env.example` lists the ports, both databases' credentials and the
demo users. Beyond those, the settings worth knowing about:

| Variable                       | Default | Controls                                 |
|--------------------------------|---------|-------------------------------------------|
| `RECON_CHUNK_SIZE`             | 500     | rows per chunk                            |
| `RECON_GRID_SIZE`              | 8       | partition shards                          |
| `RECON_WORKER_CONCURRENCY`     | 4       | partitions reconciled at once             |
| `RECON_TOKEN_TTL_MINUTES`      | 60      | access token lifetime                     |
| `RECON_CORS_ALLOWED_ORIGINS`   | dev + preview origins | browser origins allowed to call the API |

Tolerance profiles are `recon.tolerance.profiles.*` in `application.yml`; adding one needs no code.

The demo user block is exactly the thing a real deployment deletes: it is three `{noop}` passwords in
configuration so the demo is readable. Point the resource server at an identity provider's JWKS and
none of it is reachable.

---

## Notes on the stack

Java 26 on Spring Boot 4 with Spring Batch 6, MyBatis for the legacy side, JPA and Flyway for the
owned side, and virtual threads enabled — both the HTTP threads and the batch workers spend their
time blocked on two databases, which is the workload virtual threads exist for and removes the need
to guess a pool size. The console is React with MUI and Redux Toolkit, built and tested with bun.
Exact versions live in `gradle/libs.versions.toml` and `frontend/package.json`, which are the single
source of truth for them.

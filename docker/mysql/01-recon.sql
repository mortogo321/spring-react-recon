-- MySQL owns nothing beyond the database itself: Flyway creates and versions every table at API
-- startup, so there is one migration history rather than a container script and Flyway disagreeing.
--
-- Character set is pinned here because it is the one thing that cannot be fixed later without a
-- table rebuild: merchant legal names arrive with Thai characters and 4-byte emoji in free text.
ALTER DATABASE recon CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Deadlock and lock-wait diagnostics for the batch writes, on from the first run rather than
-- switched on after the first incident.
SET GLOBAL innodb_print_all_deadlocks = ON;

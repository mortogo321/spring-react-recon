#!/usr/bin/env bash
# Runs once, on the container's first start, before its healthcheck reports healthy — which is what
# makes `depends_on: condition: service_healthy` in docker-compose.yml a real gate rather than a sleep.
#
# Two accounts, deliberately:
#   RECON_LEGACY  owns the tables. Has no CREATE SESSION, so nothing can log in as the owner.
#   ${APP_USER}   the API's account. SELECT only, on those tables only.
#
# That split is the point of the exercise. Reconciliation reads someone else's system of record; if
# the credentials it holds can write to that system, the reconciliation is not a control any more.
set -euo pipefail

APP_USER="${ORACLE_APP_USER:-recon_ro}"
APP_PASSWORD="${ORACLE_APP_PASSWORD:-recon_ro}"
OWNER="RECON_LEGACY"

echo "[oracle-init] creating schema owner ${OWNER} and read-only account ${APP_USER}"

# The image puts the PDB's service name in ORACLE_DATABASE; sqlplus is on the PATH as oracle.
sqlplus -s / as sysdba <<SQL
WHENEVER SQLERROR EXIT SQL.SQLCODE
ALTER SESSION SET CONTAINER = ${ORACLE_DATABASE:-FREEPDB1};

-- Schema owner. No CREATE SESSION on purpose: the account exists to hold objects, not to be used.
CREATE USER ${OWNER} NO AUTHENTICATION
    DEFAULT TABLESPACE USERS
    QUOTA UNLIMITED ON USERS;
GRANT CREATE TABLE, CREATE SEQUENCE TO ${OWNER};

-- The account the API connects as.
CREATE USER ${APP_USER} IDENTIFIED BY "${APP_PASSWORD}"
    DEFAULT TABLESPACE USERS
    QUOTA 0 ON USERS;
GRANT CREATE SESSION TO ${APP_USER};
EXIT
SQL

echo "[oracle-init] applying legacy DDL"
sqlplus -s / as sysdba <<SQL
WHENEVER SQLERROR EXIT SQL.SQLCODE
ALTER SESSION SET CONTAINER = ${ORACLE_DATABASE:-FREEPDB1};
ALTER SESSION SET CURRENT_SCHEMA = ${OWNER};
@/legacy-sql/oracle-schema.sql
@/legacy-sql/oracle-demo-data.sql
COMMIT;
EXIT
SQL

# Grants are issued after the DDL, and enumerated rather than granted at schema level: a table
# added to the legacy system later must be granted deliberately, not inherited.
echo "[oracle-init] granting SELECT to ${APP_USER}"
sqlplus -s / as sysdba <<SQL
WHENEVER SQLERROR EXIT SQL.SQLCODE
ALTER SESSION SET CONTAINER = ${ORACLE_DATABASE:-FREEPDB1};
GRANT SELECT ON ${OWNER}.MERCHANT_MASTER TO ${APP_USER};
GRANT SELECT ON ${OWNER}.STG_SETTLEMENT_TXN TO ${APP_USER};
-- Belt as well as braces: the pool already issues ALTER SESSION SET CURRENT_SCHEMA from
-- recon.legacy.datasource.schema, so the mappers' unqualified table names resolve either way.
CREATE OR REPLACE SYNONYM ${APP_USER}.MERCHANT_MASTER FOR ${OWNER}.MERCHANT_MASTER;
CREATE OR REPLACE SYNONYM ${APP_USER}.STG_SETTLEMENT_TXN FOR ${OWNER}.STG_SETTLEMENT_TXN;
EXIT
SQL

echo "[oracle-init] done"

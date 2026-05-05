# Docker Compose Demo

This folder contains a local Docker Compose deployment for Adaptive SQL Execution Gateway with three Oracle databases.

## What Runs

- `gateway`: Spring Boot service exposed at `http://localhost:8080/rjp`
- `oracle-db1`: Oracle database for gateway connection `DB1`, exposed on `localhost:11521`
- `oracle-db2`: Oracle database for gateway connection `DB2`, exposed on `localhost:11522`
- `oracle-db3`: Oracle database for gateway connection `DB3`, exposed on `localhost:11523`

Each Oracle container creates the `GT` app user through the Oracle image `APP_USER` settings and loads the schema from:

```text
../src/test/resources/schema.sql
```

That schema creates:

- `CUSTOMER` table with primary key `CUSTOMER_PK`
- `ORDR` table with primary key `ORDR_PK`
- foreign key `ORDR_CUSTOMER_FK` from `ORDR.CUSTOMER_ID` to `CUSTOMER.ID`
- `test_pkh` package with test procedures and functions

## Files

- `docker-compose.yml`: starts the gateway and three Oracle databases
- `Dockerfile`: builds the Spring Boot jar into a runnable container image
- `../.dockerignore`: excludes local/build-only files from the Docker build context
- `docker/oracle/init/01-load-schema.sql`: sets the `GT` schema context and runs `schema.sql`
- `docker/curl-demo.sh`: sends curl requests to the running gateway

## Start Demo

From the project root:

```bash
docker compose -f demo/docker-compose.yml up --build
```

Wait until all Oracle containers are healthy and the gateway starts. The first startup can take several minutes because Oracle images need to initialize their data files.

If you previously started the demo before this configuration was applied and see `ORA-01045: Login denied. User GT does not have CREATE SESSION privilege`, remove the old database volumes and start again:

```bash
docker compose -f demo/docker-compose.yml down -v
docker compose -f demo/docker-compose.yml up --build
```

Oracle initialization scripts run only when a database volume is created. Existing volumes keep the old user state.

## Run Requests

From the project root:

```bash
./demo/docker/curl-demo.sh
```

The script uses:

```text
BASE_URL=http://localhost:8080/rjp
```

Override it if needed:

```bash
BASE_URL=http://localhost:9090/rjp ./demo/docker/curl-demo.sh
```

## What The Script Tests

### Single Stored Procedure Call

Endpoint:

```text
POST /rjp/dynpst/test_pkh.proc_with_OutParam
```

Tests:

- request envelope parsing
- routing by `_rjp.connectionName`
- stored procedure execution through descriptor alias
- input parameters from `params`
- output parameter mapping from Oracle procedure result

### Prepared Statement Call

Endpoint:

```text
POST /rjp/dynpst/prepared_statement
```

Tests:

- prepared statement descriptor loading
- positional JDBC parameter binding
- query execution against `CUSTOMER`
- `_rjp_connectionName` response metadata

### Batch Request

Endpoint:

```text
POST /rjp/batch/test_pkh.tst_function
```

Tests:

- list request envelope parsing
- batch execution
- partitioning through configured batch thread settings
- stored function execution for multiple input rows

### Mixed-Database Batch Request

Endpoint:

```text
POST /rjp/batch/prepared_statement
```

Tests:

- one batch routed across `DB1`, `DB2`, and `DB3`
- per-item database routing through `_rjp.connectionName`
- parallel execution paths against multiple Oracle containers
- prepared statement execution against each database

### Async Execution

Endpoint:

```text
POST /rjp/startAsyncTask/test_pkh.tst_function
GET /rjp/taskStatus/{taskId}
GET /rjp/taskResult/{taskId}
```

Tests:

- async task creation
- background batch execution
- task status polling
- async result retrieval

### Metrics Endpoint

Endpoints:

```text
GET /rjp/actuator/metrics
GET /rjp/actuator/metrics/http.requests.rjp.dynpst.count
```

Tests:

- Actuator exposure
- custom request counter registration
- request counting for gateway endpoints

## Manual Smoke Requests

Prepared statement:

```bash
curl -fsS -X POST http://localhost:8080/rjp/dynpst/prepared_statement \
  -H 'Content-Type: application/json' \
  -d '{
    "_rjp": {
      "connectionName": "DB1"
    },
    "params": {
      "AN": 7
    }
  }'
```

Mixed-database batch:

```bash
curl -fsS -X POST http://localhost:8080/rjp/batch/prepared_statement \
  -H 'Content-Type: application/json' \
  -d '[
    {
      "_rjp": {
        "connectionName": "DB1"
      },
      "params": {
        "AN": 3
      }
    },
    {
      "_rjp": {
        "connectionName": "DB2"
      },
      "params": {
        "AN": 7
      }
    },
    {
      "_rjp": {
        "connectionName": "DB3"
      },
      "params": {
        "AN": 9
      }
    }
  ]'
```

## Stop Demo

Stop containers while keeping database volumes:

```bash
docker compose -f demo/docker-compose.yml down
```

Stop containers and remove database volumes:

```bash
docker compose -f demo/docker-compose.yml down -v
```

Use `down -v` when you want Oracle to re-run initialization scripts on the next startup.

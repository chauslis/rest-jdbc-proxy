# Adaptive SQL Execution Gateway

Adaptive SQL Execution Gateway is a Spring Boot application that exposes a REST API for executing JDBC operations against one or more configured databases. It supports dynamic database routing, prepared statement aliases, stored procedures, stored functions, batch execution, and asynchronous task execution.

The main use case is to submit one HTTP request containing many database operation inputs. Each item can target a different configured database connection, and the service can process the dataset in parallel by splitting the input list into partitions.

This is useful for systems where data is distributed across shards, partitions, tenants, or legacy database instances, and where clients need one HTTP API instead of direct JDBC access to every database.

## Features

- **Multi-Database Support**: Works with JDBC-compatible databases such as Oracle, PostgreSQL, MySQL, SQL Server, and others
- **Explicit Request Envelope**: Separates gateway routing metadata from JDBC/business parameters
- **Dynamic Connection Switching**: Routes every request item by `_rjp.connectionName`
- **Prepared Statements**: Executes parameterized SQL through descriptor aliases
- **Stored Procedures and Functions**: Exposes database packages, procedures, and functions as REST endpoints
- **Batch Operations**: Processes multiple request items in one call
- **Parallel Batch Execution**: Splits one input dataset into partitions and executes those partitions with a configured executor
- **Mixed-Database Batch Requests**: Runs one batch across different database connections by setting `_rjp.connectionName` per item
- **Asynchronous Execution**: Starts long-running batch work and tracks task status/results
- **Demo Raw SQL Endpoint**: Optional diagnostic `/query` endpoint controlled by `app.demo-mode`
- **Request Metrics**: Micrometer request counters tagged by method, normalized URI, and status
- **Coverage Gate**: JaCoCo verification with a minimum 70% instruction coverage threshold

## Request Contract

Gateway metadata and JDBC parameters are intentionally separated.

Single request:

```json
{
  "_rjp": {
    "connectionName": "DB1"
  },
  "params": {
    "ID": 123,
    "NAME": "test",
    "P": "value"
  }
}
```

Batch request:

```json
[
  {
    "_rjp": {
      "connectionName": "DB1"
    },
    "params": {
      "ID": 123,
      "NAME": "test1"
    }
  },
  {
    "_rjp": {
      "connectionName": "DB2"
    },
    "params": {
      "ID": 223,
      "NAME": "test2"
    }
  }
]
```

`_rjp` contains gateway metadata. `params` contains only JDBC/business parameters. Database routing uses only `_rjp.connectionName`; the service does not read a top-level `connection` field and does not remove `connection` from `params`.

This avoids conflicts when a real database operation has parameters named `connection`, `result`, `alias`, `status`, or other names that could otherwise collide with gateway fields.

Example with a business parameter named `connection`:

```json
{
  "_rjp": {
    "connectionName": "DB1"
  },
  "params": {
    "connection": "business-value",
    "ID": 123
  }
}
```

The request routes to `DB1`. The `params.connection` value remains a business parameter.

Invalid envelopes, such as missing `_rjp`, missing `_rjp.connectionName`, or missing `params`, return `400 BAD_REQUEST`. Missing aliases return `404 NOT_FOUND`.

Prepared statement result rows include `_rjp_connectionName` when the gateway adds routing context to the response. The service does not add a `connection` field to result rows.

## Use Cases

### Sharded or Partitioned Databases

Adaptive SQL Execution Gateway can execute one logical request across many physical database connections. A client can build a request where each input row already knows its target shard:

```json
[
  {
    "_rjp": { "connectionName": "DB_SHARD_01" },
    "params": { "customerId": 1001 }
  },
  {
    "_rjp": { "connectionName": "DB_SHARD_02" },
    "params": { "customerId": 2204 }
  },
  {
    "_rjp": { "connectionName": "DB_SHARD_03" },
    "params": { "customerId": 3108 }
  }
]
```

This is useful when the application layer owns shard lookup or when an upstream service already resolved the partition key.

### Tenant-Based Database Routing

In multi-tenant systems, each tenant can be mapped to a separate database connection:

```json
[
  {
    "_rjp": { "connectionName": "TENANT_A" },
    "params": { "accountId": 10 }
  },
  {
    "_rjp": { "connectionName": "TENANT_B" },
    "params": { "accountId": 10 }
  },
  {
    "_rjp": { "connectionName": "TENANT_C" },
    "params": { "accountId": 10 }
  }
]
```

The same prepared statement or stored procedure alias can be executed against each tenant database without changing endpoint code.

### Legacy System Modernization

The gateway can expose existing database packages, procedures, functions, and prepared statements as HTTP endpoints. This helps modernization projects where legacy business logic remains in the database, but new services need REST access.

Typical modernization flow:

1. Keep existing stored procedures and SQL packages in place.
2. Define JSON aliases for those operations.
3. Expose them through `/dynpst/{aliasName}` or `/batch/{aliasName}`.
4. Gradually move callers from direct JDBC or legacy middleware to HTTP.

### Parallel Data Processing

Large input datasets can be sent in one batch request. The service splits the dataset into sublists and executes those sublists with the configured executor.

This is useful for bulk validation, customer/account enrichment, fan-out reads across database partitions, parallel stored procedure execution, migration jobs, and reconciliation jobs.

## Setup

### Prerequisites

- Java 21 or later
- Gradle 7.x or later
- Any JDBC-compatible database

### Quick Start

```bash
git clone <repository-url>
cd adaptive-sql-execution-gateway
./gradlew bootRun
```

### Docker Compose Deployment

The repository includes a Docker Compose deployment with the gateway service and three Oracle databases. Each Oracle container initializes the `GT` schema from `src/test/resources/schema.sql`.

```bash
docker compose -f demo/docker-compose.yml up --build
```

The gateway HTTP API is exposed outside Docker at:

```text
http://localhost:8080/rjp
```

Oracle listener ports are also exposed for diagnostics:

```text
DB1: localhost:11521
DB2: localhost:11522
DB3: localhost:11523
```

Run the curl demonstration script after the containers are healthy:

```bash
./demo/docker/curl-demo.sh
```

The script demonstrates a single stored procedure call, a prepared statement call, a batch request, a mixed-database batch request, async execution, and actuator metrics.

### Database Configuration

Configure database connections in `application.properties` with JDBC URLs supplied by environment variables:

```properties
Db.connections=\
  {DB1:"${DB1_JDBC_URL}",\
  DB2:"${DB2_JDBC_URL}",\
  DB3:"${DB3_JDBC_URL}"}
```

Supported JDBC URL examples:

- PostgreSQL: `jdbc:postgresql://host:port/database?user=<user>&password=<password>`
- MySQL: `jdbc:mysql://host:port/database?user=<user>&password=<password>`
- Oracle: `jdbc:oracle:thin:<user>/<password>@host:port:sid`
- SQL Server: `jdbc:sqlserver://host:port;databaseName=database;user=<user>;password=<password>`

Store actual JDBC URLs in environment variables or a local `.db.env` file that is excluded from Git.

## Descriptor Contract

Access descriptors are JSON files under `src/main/resources/access_descriptor`. The descriptor file base name is the alias used in API paths.

Example:

```text
src/main/resources/access_descriptor/prepared_statement.json
alias: prepared_statement
endpoint: /rjp/dynpst/prepared_statement
```

Prepared statement descriptor:

```json
{
  "operationDescriptor": {
    "type": "PREPARED_STATEMENT",
    "sql": "select * from customer where id <= ?",
    "inputParameters": [
      {
        "name": "AN",
        "jdbcType": "BIGINT",
        "position": 1,
        "defaultValue": 500
      }
    ]
  }
}
```

Callable statement descriptor:

```json
{
  "operationDescriptor": {
    "type": "CALLABLE_STATEMENT",
    "databaseObjectName": "test_pkh.proc_with_OutParam",
    "inputParameters": [
      {
        "name": "ID",
        "jdbcType": "BIGINT",
        "position": 1,
        "defaultValue": ""
      }
    ],
    "outputParameters": [
      {
        "name": "OUT1",
        "jdbcType": "VARCHAR",
        "position": 3
      }
    ]
  }
}
```

Stored functions are represented as `CALLABLE_STATEMENT` descriptors. If `outputParameters` contains a parameter named `RESULT` case-insensitively, the gateway calls the database object as a function.

Function descriptor:

```json
{
  "operationDescriptor": {
    "type": "CALLABLE_STATEMENT",
    "databaseObjectName": "test_pkh.tst_function",
    "inputParameters": [
      {
        "name": "AN",
        "jdbcType": "VARCHAR",
        "position": 1,
        "defaultValue": " "
      }
    ],
    "outputParameters": [
      {
        "name": "RESULT",
        "jdbcType": "VARCHAR",
        "position": 0
      }
    ]
  }
}
```

Descriptor validation requires `operationDescriptor`, `type`, supported JDBC types, and non-negative positions. Prepared statements require `sql`; callable statements require `databaseObjectName`.

## Parallel Execution Configuration

Parallel execution is controlled by configuration:

```properties
app.demo-mode=false
app.datasource.maximum-pool-size=10
app.async.max-threads-per-db=10
app.async.max-threads-total=100
app.async.task-ttl-ms=300000
app.async.task-cleanup-interval-ms=60000
app.batch.max-threads-per-request=10
```

The effective executor size is calculated from the number of configured database connections:

```text
min(number_of_databases * app.async.max-threads-per-db, app.async.max-threads-total)
```

For each batch request, the input dataset is split into sublists using `app.batch.max-threads-per-request` as the upper bound. The service never creates more partitions than there are input rows.

Example:

```text
10 input rows, app.batch.max-threads-per-request=3
=> partitions: [4 rows, 3 rows, 3 rows]
```

This gives two levels of concurrency:

1. **Across database connections**: rows in the same request can target `DB1`, `DB2`, `DB3`, etc. Each row carries its own `_rjp.connectionName`.
2. **Within one database workload**: many rows for the same database are split into partitions and executed through the shared `ExecutorService`.

Thread counts should be sized together with the JDBC pool and database capacity. For JDBC work, increasing thread count above the connection pool or database server capacity usually adds contention instead of throughput.

Completed and cancelled async tasks are retained only for `app.async.task-ttl-ms`. A scheduled cleanup runs every `app.async.task-cleanup-interval-ms` and removes expired terminal tasks. Set `app.async.task-ttl-ms=-1` to disable automatic cleanup.

## API Endpoints

### Raw SQL Query

Raw SQL execution is a demo/diagnostic feature. It works only when demo mode is enabled:

```properties
app.demo-mode=true
```

```text
GET /rjp/query?connection=DB1&sqlQuery=SELECT * FROM users
```

This endpoint intentionally remains query-parameter based. Production clients should prefer JSON aliases for prepared statements, stored procedures, and stored functions.

### Execute One Alias

```text
POST /rjp/dynpst/{aliasName}
```

```json
{
  "_rjp": {
    "connectionName": "DB1"
  },
  "params": {
    "AN": 123
  }
}
```

### Execute Batch Alias

```text
POST /rjp/batch/{aliasName}
```

```json
[
  {
    "_rjp": { "connectionName": "DB1" },
    "params": { "ID": 1 }
  },
  {
    "_rjp": { "connectionName": "DB2" },
    "params": { "ID": 2 }
  },
  {
    "_rjp": { "connectionName": "DB3" },
    "params": { "ID": 3 }
  }
]
```

The service splits the list into partitions and executes the partitions in parallel. During execution, the dynamic data source context is set from each item's `_rjp.connectionName` before calling JDBC.

### Async Batch Alias

```text
POST /rjp/startAsyncTask/{aliasName}
GET /rjp/taskStatus/{taskId}
GET /rjp/taskResult/{taskId}
DELETE /rjp/task/{taskId}
```

Start async task:

```bash
TASK_ID=$(curl -X POST http://localhost:8080/rjp/startAsyncTask/my_procedure \
  -H "Content-Type: application/json" \
  -d '[
    {
      "_rjp": { "connectionName": "DB1" },
      "params": { "param1": "value1" }
    }
  ]')
```

Check status and result:

```bash
curl "http://localhost:8080/rjp/taskStatus/$TASK_ID"
curl "http://localhost:8080/rjp/taskResult/$TASK_ID"
```

## Example Usage

Demo-mode raw SQL:

```bash
curl "http://localhost:8080/rjp/query?connection=DB1&sqlQuery=SELECT * FROM employees"
```

Prepared statement alias:

```bash
curl -X POST http://localhost:8080/rjp/dynpst/prepared_statement \
  -H "Content-Type: application/json" \
  -d '{
    "_rjp": { "connectionName": "DB1" },
    "params": { "AN": 500 }
  }'
```

Stored procedure/function alias:

```bash
curl -X POST http://localhost:8080/rjp/dynpst/test_pkh.tst_function \
  -H "Content-Type: application/json" \
  -d '{
    "_rjp": { "connectionName": "DB1" },
    "params": { "AN": "123" }
  }'
```

Mixed-database batch:

```bash
curl -X POST http://localhost:8080/rjp/batch/prepared_statement \
  -H "Content-Type: application/json" \
  -d '[
    {
      "_rjp": { "connectionName": "DB1" },
      "params": { "AN": 3 }
    },
    {
      "_rjp": { "connectionName": "DB2" },
      "params": { "AN": 7 }
    }
  ]'
```

## Testing and Coverage

Run the full verification suite:

```bash
./gradlew check
```

The suite includes unit tests for request envelope validation, dynamic data source context handling, dataset splitting, mixed-database batch parameters, async task management, controller delegation, request metrics, descriptor loading, Oracle-backed integration tests using Testcontainers, and JaCoCo coverage verification.

The HTML coverage report is generated at:

```text
build/reports/jacoco/test/html/index.html
```

## Execution Model

Adaptive SQL Execution Gateway uses `CompletableFuture` with a shared `ExecutorService` for parallel database work.

For synchronous batch endpoints such as `/batch/{aliasName}`, the request waits until all partitions complete and then returns the combined result.

For asynchronous endpoints such as `/startAsyncTask/{aliasName}`, the request immediately returns a task ID. The batch work continues in the executor, and clients can poll task status or fetch the result later.

This keeps the threading model centralized:

- controllers do not create threads
- services submit partitioned JDBC work to the configured executor
- async task tracking stores `CompletableFuture` instances by task ID
- per-item database routing is driven by `_rjp.connectionName`

## License

This project is licensed under the MIT License.

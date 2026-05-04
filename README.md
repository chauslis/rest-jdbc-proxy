# Adaptive SQL Execution Gateway

Adaptive SQL Execution Gateway is a Spring Boot application that exposes a REST API for executing JDBC operations against one or more configured databases. It supports dynamic database routing, SQL queries, prepared statement aliases, stored procedures, stored functions, batch execution, and asynchronous task execution.

The main use case is to submit one HTTP request that contains many database operation parameters. Each item can target a different configured database connection, and the service can process the dataset in parallel by splitting the input list into partitions.

This makes the service useful for systems where data is distributed across shards, partitions, tenants, or legacy database instances, and where clients need one HTTP API instead of direct JDBC access to every database.

## Features

- **Multi-Database Support**: Works with any JDBC-compatible database (PostgreSQL, MySQL, Oracle, SQL Server, etc.)
- **Dynamic Connection Switching**: Switch between multiple database connections at runtime
- **REST API**: Clean RESTful endpoints for database operations
- **Stored Procedures & Functions**: Execute database stored procedures and functions
- **Batch Operations**: Process multiple operations efficiently
- **Parallel Batch Execution**: Split one input dataset into partitions and execute the partitions with a configured executor
- **Mixed-Database Batch Requests**: Run one batch request across different database connections by setting `connection` per input item
- **Asynchronous Execution**: Non-blocking database operations with task tracking
- **Prepared Statements**: Parameterized query support for security
- **CORS Support**: Web application integration ready
- **Request Metrics**: Micrometer request counters tagged by method, normalized URI, and status
- **Coverage Gate**: JaCoCo verification with a minimum 70% instruction coverage threshold

## Use Cases

### Sharded or Partitioned Databases

Adaptive SQL Execution Gateway can execute one logical request across many physical database connections. A client can build a request where each input row already knows its target shard:

```json
[
  { "connection": "DB_SHARD_01", "customerId": 1001 },
  { "connection": "DB_SHARD_02", "customerId": 2204 },
  { "connection": "DB_SHARD_03", "customerId": 3108 }
]
```

The service routes each item to the database named by `connection`. This is useful when the application layer owns shard lookup or when an upstream service already resolved the partition key.

### Tenant-Based Database Routing

In multi-tenant systems, each tenant can be mapped to a separate database connection:

```json
[
  { "connection": "TENANT_A", "accountId": 10 },
  { "connection": "TENANT_B", "accountId": 10 },
  { "connection": "TENANT_C", "accountId": 10 }
]
```

The same prepared statement or stored procedure alias can be executed against each tenant database without changing endpoint code.

### Legacy System Modernization

Adaptive SQL Execution Gateway can expose existing database packages, procedures, functions, and prepared statements as HTTP endpoints. This helps modernization projects where legacy business logic remains in the database, but new services need REST access.

Typical modernization flow:

1. Keep existing stored procedures and SQL packages in place.
2. Define JSON aliases for those operations.
3. Expose them through `/dynpst/{aliasName}` or `/batch/{aliasName}`.
4. Gradually move callers from direct JDBC or legacy middleware to HTTP.

### Parallel Data Processing

Large input datasets can be sent in one batch request. The service splits the dataset into sublists and executes those sublists with the configured executor.

This is useful for:

- bulk validation
- customer/account enrichment
- parallel stored procedure execution
- fan-out reads across database partitions
- migration or reconciliation jobs

## Setup

### Prerequisites

- Java 21 or later
- Gradle 7.x or later
- Any JDBC-compatible database

### Quick Start

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd adaptive-sql-execution-gateway
   ```

2. **Configure your databases**
   ```bash
   cp application.properties.example application.properties
   ```
   Edit `application.properties` with your database connection details.

3. **Build and run**
   ```bash
   ./gradlew bootRun
   ```

### Database Configuration

The application supports multiple database types. Configure connections in `application.properties`:

```properties
# Multiple database examples
Db.connections=\
  {DB1:"${DB1_JDBC_URL}",\
  DB2:"${DB2_JDBC_URL}",\
  DB3:"${DB3_JDBC_URL}"}
```

**Supported Databases:**
- PostgreSQL: `jdbc:postgresql://host:port/database?user=<user>&password=<password>`
- MySQL: `jdbc:mysql://host:port/database?user=<user>&password=<password>`
- Oracle: `jdbc:oracle:thin:<user>/<password>@host:port:sid`
- SQL Server: `jdbc:sqlserver://host:port;databaseName=database;user=<user>;password=<password>`

Store actual JDBC URLs in environment variables or a local `.db.env` file that is excluded from Git.

Access descriptors for stored procedures and prepared statements are defined in JSON files in the `src/main/resources/access_descriptor` directory.

### Parallel Execution Configuration

Parallel execution is controlled by configuration rather than hardcoded thread constants:

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

1. **Across database connections**: rows in the same request can target `DB1`, `DB2`, `DB3`, etc. Each row carries its own `connection` value.
2. **Within one database workload**: many rows for the same database are split into partitions and executed through the shared `ExecutorService`.

Thread counts should be sized together with the JDBC pool and database capacity. For JDBC work, increasing thread count above the connection pool or database server capacity usually adds contention instead of throughput.

Completed and cancelled async tasks are retained only for `app.async.task-ttl-ms`. A scheduled cleanup runs every `app.async.task-cleanup-interval-ms` and removes expired terminal tasks. Set `app.async.task-ttl-ms=-1` to disable automatic cleanup.

`app.demo-mode` controls the raw SQL `/query` endpoint. It is disabled by default and should be enabled only for local demos or controlled diagnostics.

## API Endpoints

### Raw SQL Queries

Raw SQL execution is a demo/diagnostic feature. It works only when demo mode is enabled:

```properties
app.demo-mode=true
```

```
GET /rjp/query?connection=DB1&sqlQuery=SELECT * FROM users
```

Production clients should prefer JSON aliases for prepared statements, stored procedures, and stored functions.

### Stored Procedures/Functions

```
POST /rjp/dynpst/{aliasName}
```
With request body:
```json
{
  "connection": "DB1",
  "param1": "value1",
  "param2": "value2"
}
```

### Batch Operations

```
POST /rjp/batch/{aliasName}
```
With request body containing a list of parameter maps.

Each item in the list may specify a different database connection:

```json
[
  { "connection": "DB1", "ID": 1 },
  { "connection": "DB2", "ID": 2 },
  { "connection": "DB1", "ID": 3 },
  { "connection": "DB3", "ID": 4 }
]
```

The service splits this list into partitions and executes the partitions in parallel. During execution, the dynamic data source context is set from each item's `connection` field before calling JDBC.

### Asynchronous Operations

```
POST /rjp/startAsyncTask/{aliasName}
```

Check status:
```
GET /rjp/taskStatus/{taskId}
```

Get result:
```
GET /rjp/taskResult/{taskId}
```

## Example Usage

### Demo-Mode Raw SQL Query

Requires:

```properties
app.demo-mode=true
```

```bash
curl "http://localhost:8080/rjp/query?connection=DB1&sqlQuery=SELECT * FROM employees"
```

### Stored Procedure Call
```bash
curl -X POST http://localhost:8080/rjp/dynpst/my_procedure \
  -H "Content-Type: application/json" \
  -d '{
    "connection": "DB1",
    "param1": "value1",
    "param2": "value2"
  }'
```

### Batch Operation
```bash
curl -X POST http://localhost:8080/rjp/batch/my_procedure \
  -H "Content-Type: application/json" \
  -d '[
    {"connection": "DB1", "param1": "value1"},
    {"connection": "DB2", "param1": "value2"},
    {"connection": "DB1", "param1": "value3"}
  ]'
```

### Async Operation
```bash
# Start async task
TASK_ID=$(curl -X POST http://localhost:8080/rjp/startAsyncTask/my_procedure \
  -H "Content-Type: application/json" \
  -d '[{"connection": "DB1", "param1": "value1"}]')

# Check status
curl "http://localhost:8080/rjp/taskStatus/$TASK_ID"

# Get result
curl "http://localhost:8080/rjp/taskResult/$TASK_ID"
```

## Testing and Coverage

Run the full verification suite:

```bash
./gradlew check
```

The suite includes:

- Unit tests for dynamic data source context handling
- Unit tests for dataset splitting and mixed-database batch parameters
- Unit tests for async task status/result management
- Unit tests for controller delegation and request metrics
- Unit tests for alias descriptor loading
- Oracle-backed integration tests using Testcontainers
- JaCoCo coverage verification with a minimum 70% threshold

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
- per-row database routing is driven by the `connection` field

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

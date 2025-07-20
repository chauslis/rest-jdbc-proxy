# RestJdbcProxy

RestJdbcProxy is a Spring Boot application that provides a RESTful API for accessing databases via JDBC. It allows for dynamic switching between different database connections and supports SQL queries, stored procedures, and functions.

## Features

- Dynamic database connection switching
- REST endpoints for executing SQL queries
- Support for Oracle stored procedures and functions
- Batch execution of database operations
- Asynchronous execution of database operations
- Prepared statement support
- CORS support for web applications

## Setup

### Prerequisites

- Java 17
- Gradle
- Oracle Database (tested with Oracle XE)

### Configuration

Database connections are configured in `application.properties`:

```properties
Db.connections=\
  {DB1:"jdbc:oracle:thin:username/password@localhost:11521/XEPDB1",\
  DB2:"jdbc:oracle:thin:username/password@localhost:11521/XEPDB1",\
  DB3:"jdbc:oracle:thin:username/password@localhost:11521/XEPDB1"}
```

Access descriptors for stored procedures and prepared statements are defined in JSON files in the `src/main/resources/access_descriptor` directory.

## API Endpoints

### SQL Queries

```
GET /rjp/query?connection=DB1&sqlQuery=SELECT * FROM users
```

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

Execute a simple query:
```
GET /rjp/query?connection=DB1&sqlQuery=SELECT * FROM employees
```

Call a stored procedure:
```
POST /rjp/dynpst/test_pkh.proc_with_Param
```
```json
{
  "connection": "DB1",
  "p_in_param": "test value"
}
```

## License

[Add license information here]
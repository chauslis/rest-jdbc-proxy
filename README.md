# RestJdbcProxy

RestJdbcProxy is a Spring Boot application that provides a RESTful API for accessing any JDBC-compatible database. It allows for dynamic switching between different database connections and supports SQL queries, stored procedures, and functions with asynchronous execution capabilities.

## Features

- **Multi-Database Support**: Works with any JDBC-compatible database (PostgreSQL, MySQL, Oracle, SQL Server, etc.)
- **Dynamic Connection Switching**: Switch between multiple database connections at runtime
- **REST API**: Clean RESTful endpoints for database operations
- **Stored Procedures & Functions**: Execute database stored procedures and functions
- **Batch Operations**: Process multiple operations efficiently
- **Asynchronous Execution**: Non-blocking database operations with task tracking
- **Prepared Statements**: Parameterized query support for security
- **CORS Support**: Web application integration ready

## Setup

### Prerequisites

- Java 17 or later
- Gradle 7.x or later
- Any JDBC-compatible database

### Quick Start

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd RestJdbcProxy
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
  {DB1:"jdbc:postgresql://localhost:5432/mydb?user=username&password=password",\
  DB2:"jdbc:mysql://localhost:3306/mydb?user=username&password=password",\
  DB3:"jdbc:oracle:thin:username/password@localhost:1521:xe"}
```

**Supported Databases:**
- PostgreSQL: `jdbc:postgresql://host:port/database?user=username&password=password`
- MySQL: `jdbc:mysql://host:port/database?user=username&password=password`
- Oracle: `jdbc:oracle:thin:username/password@host:port:sid`
- SQL Server: `jdbc:sqlserver://host:port;databaseName=database;user=username;password=password`

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

### Simple SQL Query
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
    {"connection": "DB1", "param1": "value2"}
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

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.
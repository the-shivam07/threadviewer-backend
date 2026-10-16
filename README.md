# ThreadViewer

ThreadViewer is a Java-based application for analyzing Java thread
dumps. The current backend parses thread-dump files, summarizes thread
states, detects deadlocks, and stores analysis summaries in PostgreSQL.

> Project status: Thread dump backend and basic analysis history API are
> implemented and tested. React frontend, heap-dump analysis, and
> additional memory-analysis capabilities are planned.

## Current Features

-   Upload a thread dump as a text file.
-   Parse thread information such as thread name, ID, state, daemon
    status, priority, stack trace, owned locks, and waiting lock.
-   Summarize total, daemon, and non-daemon threads.
-   List blocked and waiting threads.
-   Detect deadlocks and identify threads involved in a detected cycle.
-   Save analysis summaries to PostgreSQL.
-   Retrieve saved analysis history through a REST API.

## Technology Stack

-   Java 21
-   Spring Boot
-   Spring Web / REST APIs
-   Spring Data JPA / Hibernate
-   PostgreSQL
-   Maven
-   JUnit tests

The frontend is planned with React. Heap-dump and broader
memory-analysis features are also planned.

## Prerequisites

Install the following before running the backend:

-   JDK 21
-   Maven (or use the Maven Wrapper if available)
-   PostgreSQL
-   An IDE such as IntelliJ IDEA, Eclipse, or Spring Tool Suite

## Database Setup

1.  Create a PostgreSQL database named `threadviewer_db`.
2.  Open `src/main/resources/application.properties`.
3.  Configure the datasource username and password for your local
    PostgreSQL installation.

Example configuration:

``` properties
spring.application.name=threadviewer-backend
spring.datasource.url=jdbc:postgresql://localhost:5432/threadviewer_db
spring.datasource.username=postgres
spring.datasource.password=YOUR_LOCAL_PASSWORD

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
server.port=8081
```

Replace `YOUR_LOCAL_PASSWORD` with your own local password. Do not
commit real passwords or other secrets to GitHub.

## Run the Backend

From the backend project directory, run:

``` bash
mvn spring-boot:run
```

The backend is configured to run on:

``` text
http://localhost:8081
```

To run tests:

``` bash
mvn test
```

The last reported test run had 19 tests with 0 failures and 0 errors.

## API Endpoints

### 1. Analyze a thread dump directly

``` http
POST /api/v1/thread/analyze
Content-Type: application/json
```

This endpoint accepts a `ThreadDumpData` JSON payload.

### 2. Upload a thread dump file

``` http
POST /api/v1/thread/upload
Content-Type: multipart/form-data
```

Use a form-data field named `file` with type `File`.

Example local URL:

``` text
http://localhost:8081/api/v1/thread/upload
```

### 3. Get analysis history

``` http
GET /api/v1/thread/history
```

Example local URL:

``` text
http://localhost:8081/api/v1/thread/history
```

This endpoint returns saved analysis summaries, including the record ID,
creation time, thread counts, blocked/waiting counts, and deadlock
status.

## Testing with Postman

1.  Start the Spring Boot backend.
2.  Create a `POST` request to
    `http://localhost:8081/api/v1/thread/upload`.
3.  Select **Body → form-data**.
4.  Add a key named `file`, set its type to **File**, and select a
    `.txt` thread dump.
5.  Send the request and inspect the JSON response.
6.  Send a `GET` request to
    `http://localhost:8081/api/v1/thread/history` to view saved analysis
    records.

A valid test should include both: - A dump containing a deadlock, where
`deadlockDetected` should be `true`. - A dump without a deadlock, where
`deadlockDetected` should be `false`.

## Project Structure

The backend currently uses packages for responsibilities such as:

-   `controller` --- REST endpoints
-   `service` --- analysis and history logic
-   `parser` --- thread-dump parsing
-   `analysis` --- deadlock detection and analysis logic
-   `dto` --- request and response objects
-   `entity` --- JPA entities
-   `repository` --- database access

Check the source tree for the exact current package and class names.

## Roadmap

-   [x] Thread-dump upload and parsing
-   [x] Thread-state summaries
-   [x] Deadlock detection
-   [x] PostgreSQL persistence for analysis summaries
-   [x] Basic analysis history API
-   [ ] React dashboard
-   [ ] Frontend-to-backend integration
-   [ ] Heap-dump analysis
-   [ ] Additional memory-analysis features
-   [ ] Final integration tests and deployment documentation

## Security Notes

-   Keep local database credentials out of source control.
-   For production, use environment variables or a secrets manager.
-   Review file-size limits and input validation before exposing upload
    endpoints publicly.
-   Configure CORS deliberately when the React frontend is connected.

## License

No license has been selected yet. Add a license before distributing the
project publicly.

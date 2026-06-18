# Location Gateway

A Spring Boot REST API for UK location and postcode lookups, built with Java 17 and Spring Boot 3.2.0.

## Features

- **Location Search**: Search for UK locations by query string
- **Postcode Lookup**: Retrieve location details from UK postcodes using the Postcode.io API
- **Reactive Support**: Built with Spring WebFlux for asynchronous operations
- **Health Monitoring**: Spring Boot Actuator endpoints included

## Technology Stack

- **Java 17**
- **Spring Boot 3.2.0**
- **Spring Web MVC** - REST API endpoints
- **Spring WebFlux** - Reactive programming support
- **Spring Boot Actuator** - Health and monitoring endpoints
- **Lombok** - Boilerplate code reduction
- **Maven** - Build and dependency management

## API Endpoints

### Search Locations
```
GET /api/locations?q={query}
```
Search for UK locations by name or partial match.

**Example:**
```bash
curl "http://localhost:8080/api/locations?q=London"
```

**Response:**
```json
{
  "status": 200,
  "success": true,
  "message": "Retrieved 5 matching UK locations.",
  "data": [
    {
      "name": "London",
      "region": "London",
      "country": "England"
    }
  ]
}
```

### Get Location by Postcode
```
GET /api/postcodes/{postcode}
```
Retrieve location details for a specific UK postcode using the Postcode.io API.

**Example:**
```bash
curl "http://localhost:8080/api/postcodes/SW1A1AA"
```

**Response:**
```json
{
  "status": 200,
  "success": true,
  "message": "Retrieved location for postcode SW1A1AA.",
  "data": [
    {
      "postcode": "SW1A 1AA",
      "latitude": 51.5035,
      "longitude": -0.1277,
      "region": "London",
      "country": "England"
    }
  ]
}
```

## Building the Project

### Prerequisites

- Java 17 or higher
- Maven 3.6+

### Build Commands

```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Package the application
mvn clean package

# Run the application
mvn spring-boot:run
```

## Running the Application

The application will start on `http://localhost:8080` by default.

### Configuration

Application properties can be configured in `src/main/resources/application.properties`.

## Project Structure

```
location-gateway/
├── src/
│   ├── main/
│   │   ├── java/com/jobseekercopilot/locationgateway/
│   │   │   ├── LocationGatewayApplication.java
│   │   │   ├── client/
│   │   │   │   └── PostcodeIoClient.java
│   │   │   ├── controller/
│   │   │   │   └── LocationController.java
│   │   │   ├── model/
│   │   │   │   ├── Location.java
│   │   │   │   └── LocationResponse.java
│   │   │   └── service/
│   │   │       └── LocationService.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/com/jobseekercopilot/locationgateway/
│           ├── LocationControllerIntegrationTest.java
│           └── service/
│               └── LocationServiceTest.java
├── pom.xml
├── Dockerfile
└── README.md
```

## Testing

The project includes both unit and integration tests:

- **LocationServiceTest**: Unit tests for the location service layer
- **LocationControllerIntegrationTest**: Integration tests for the REST API endpoints

Run tests with:
```bash
mvn test
```

## Docker

A Dockerfile is included for containerized deployment.

```bash
# Build Docker image
docker build -t location-gateway .

# Run container
docker run -p 8080:8080 location-gateway
```

## API Response Format

All API responses follow a consistent structure:

```json
{
  "status": 200,
  "success": true,
  "message": "Description of the result",
  "data": [...]
}
```

- `status`: HTTP status code
- `success`: Boolean indicating success/failure
- `message`: Human-readable message
- `data`: Response payload (array or object)

## Error Handling

The API returns appropriate HTTP status codes and error messages:

- `400 Bad Request`: Missing or invalid parameters
- `500 Internal Server Error`: Server-side errors

## License

This project is part of the Job Seeker Copilot suite.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## Repository

https://github.com/mcgeeverbernard1992/location-gateway
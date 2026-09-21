package com.example.myapplication.network;

/**
 * Cuerpo de respuesta de Spring Boot Actuator: GET /actuator/health -> {"status":"UP"}.
 */
public class HealthResponse {
    private String status;

    public String getStatus() {
        return status;
    }

    public boolean isUp() {
        return "UP".equalsIgnoreCase(status);
    }
}

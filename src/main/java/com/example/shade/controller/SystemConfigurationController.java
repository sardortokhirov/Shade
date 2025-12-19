package com.example.shade.controller;

import com.example.shade.model.SystemConfiguration;
import com.example.shade.service.SystemConfigurationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;

/**
 * REST API controller for system configuration management.
 * Provides endpoints to retrieve, create, and update dynamic system configuration values
 * such as top-up limits, ticket calculations, commission percentages, etc.
 * All endpoints require Basic Authentication (MaxUp1000:MaxUp1000).
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SystemConfigurationController {

    private final SystemConfigurationService configurationService;

    /**
     * Authenticates the incoming HTTP request using Basic Authentication.
     * Validates that the Authorization header contains valid credentials (MaxUp1000:MaxUp1000).
     *
     * @param request The HTTP servlet request containing the Authorization header
     * @return true if authentication is successful, false otherwise
     */
    private boolean authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String credentials = new String(Base64.getDecoder().decode(authHeader.substring(6)));
            String[] parts = credentials.split(":");
            return parts.length == 2 && "MaxUp1000".equals(parts[0]) && "MaxUp1000".equals(parts[1]);
        }
        return false;
    }

    /**
     * Retrieves the current system configuration.
     * Returns the latest configuration from the database, or creates a default configuration
     * if none exists. This includes all dynamic values like top-up limits, ticket calculations,
     * commission percentages, etc.
     *
     * @param request The HTTP servlet request for authentication
     * @return ResponseEntity containing the SystemConfiguration object, or UNAUTHORIZED if authentication fails
     */
    @GetMapping
    public ResponseEntity<SystemConfiguration> getConfiguration(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        return ResponseEntity.ok(configurationService.getConfiguration());
    }

    /**
     * Creates a new system configuration entry.
     * Saves the provided configuration as the latest configuration in the database.
     * This will be used by all services for dynamic configuration values.
     * Note: This creates a new entry with a new timestamp, maintaining configuration history.
     *
     * @param config The SystemConfiguration object containing all configuration values to be saved
     * @param request The HTTP servlet request for authentication
     * @return ResponseEntity containing the saved SystemConfiguration object, or UNAUTHORIZED if authentication fails
     */
    @PostMapping
    public ResponseEntity<SystemConfiguration> createConfiguration(
            @RequestBody SystemConfiguration config,
            HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        SystemConfiguration saved = configurationService.updateConfiguration(config);
        return ResponseEntity.ok(saved);
    }

    /**
     * Updates an existing system configuration by ID.
     * Sets the provided ID on the configuration object and saves it as a new entry
     * (maintaining configuration history with timestamp).
     *
     * @param id The ID of the configuration to update
     * @param config The SystemConfiguration object containing updated configuration values
     * @param request The HTTP servlet request for authentication
     * @return ResponseEntity containing the saved SystemConfiguration object, or UNAUTHORIZED if authentication fails
     */
    @PutMapping("/{id}")
    public ResponseEntity<SystemConfiguration> updateConfiguration(
            @PathVariable Long id,
            @RequestBody SystemConfiguration config,
            HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        config.setId(id);
        SystemConfiguration saved = configurationService.updateConfiguration(config);
        return ResponseEntity.ok(saved);
    }
}

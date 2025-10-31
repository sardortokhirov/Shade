package com.example.shade.service;

import com.example.shade.dto.PlatformRequest;
import com.example.shade.model.Currency;
import com.example.shade.model.Platform;
import com.example.shade.repository.PlatformRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PlatformService {
    private static final Logger logger = LoggerFactory.getLogger(PlatformService.class);
    private final PlatformRepository platformRepository;

    public Platform createPlatform(PlatformRequest request) {
        logger.info("Creating new platform of type: {}", request.getType());
        Platform platform = new Platform();
        mapDtoToEntity(platform, request);
        return platformRepository.save(platform);
    }

    public Platform updatePlatform(Long id, PlatformRequest request) {
        logger.info("Updating platform id: {} with type: {}", id, request.getType());
        Platform platform = platformRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Platform not found with id: " + id));
        mapDtoToEntity(platform, request);
        return platformRepository.save(platform);
    }

    /**
     * Central logic to map the request DTO to the Platform entity based on type.
     * This avoids code duplication between create and update methods.
     */
    private void mapDtoToEntity(Platform platform, PlatformRequest request) {
        // Set common properties first
        platform.setName(request.getName());
        platform.setCurrency(request.getCurrency());
        platform.setApiKey(request.getApiKey());
        platform.setType(request.getType());

        // Handle logic based on the platform type
        if ("mostbet".equalsIgnoreCase(request.getType())) {
            validateMostbetInputs(request);
            platform.setSecret(request.getSecret());
            // Null out the other params to ensure data integrity
            platform.setLogin(null);
            platform.setPassword(null);
            platform.setWorkplaceId(null);
        } else { // Default to "common"
            validateCommonInputs(request, platform.getId() == null); // Check password only for new platforms
            platform.setLogin(request.getLogin());
            platform.setWorkplaceId(request.getWorkplaceId());
            // Only update password if a new one is provided
            if (StringUtils.hasText(request.getPassword())) {
                platform.setPassword(request.getPassword());
            }
            // Null out the mostbet param
            platform.setSecret(null);
        }
    }

    private void validateCommonInputs(PlatformRequest request, boolean isNew) {
        if (!StringUtils.hasText(request.getName())) throw new IllegalArgumentException("Platform name cannot be empty");
        if (Objects.isNull(request.getCurrency())) throw new IllegalArgumentException("Currency cannot be null");
        if (!StringUtils.hasText(request.getApiKey())) throw new IllegalArgumentException("API key cannot be empty");
        if (!StringUtils.hasText(request.getLogin())) throw new IllegalArgumentException("Login cannot be empty");
        if (!StringUtils.hasText(request.getWorkplaceId())) throw new IllegalArgumentException("Workplace ID cannot be empty");
        if (isNew && !StringUtils.hasText(request.getPassword())) {
            throw new IllegalArgumentException("Password is required for new platforms");
        }
    }

    private void validateMostbetInputs(PlatformRequest request) {
        if (!StringUtils.hasText(request.getName())) throw new IllegalArgumentException("Platform name cannot be empty");
        if (Objects.isNull(request.getCurrency())) throw new IllegalArgumentException("Currency cannot be null");
        if (!StringUtils.hasText(request.getApiKey())) throw new IllegalArgumentException("API key cannot be empty");
        if (!StringUtils.hasText(request.getSecret())) throw new IllegalArgumentException("Secret key cannot be empty for Mostbet");
    }

    // --- Other methods remain largely the same ---

    public void deletePlatform(Long id) {
        logger.info("Deleting platform: id={}", id);
        if (!platformRepository.existsById(id)) {
            throw new IllegalArgumentException("Platform not found with id: " + id);
        }
        platformRepository.deleteById(id);
    }

    public List<Platform> getAllPlatforms() {
        return platformRepository.findAll();
    }

    public Platform getPlatformById(Long id) {
        return platformRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Platform not found with id: " + id));
    }
}
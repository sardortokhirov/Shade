package com.example.shade.dto;

import com.example.shade.model.Currency;

public class PlatformRequest {
    private String name;
    private Currency currency;
    private String apiKey;
    private String login;
    private String password;
    private String workplaceId;
    private String secret;
    private String type; // "common" or "mostbet"

    // Standard Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Currency getCurrency() { return currency; }
    public void setCurrency(Currency currency) { this.currency = currency; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getWorkplaceId() { return workplaceId; }
    public void setWorkplaceId(String workplaceId) { this.workplaceId = workplaceId; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; } // Corrected setter
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
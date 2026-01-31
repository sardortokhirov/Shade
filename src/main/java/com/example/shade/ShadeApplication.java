package com.example.shade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@SpringBootApplication
@EnableScheduling
public class ShadeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShadeApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(10))  // Connection timeout: 10 seconds
                .readTimeout(Duration.ofSeconds(30))     // Read timeout: 30 seconds
                .build();
    }

}
package com.example.shade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ShadeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShadeApplication.class, args);
    }

}
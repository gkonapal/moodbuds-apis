package com.moodbuds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MoodBudsApplication {
    public static void main(String[] args) {
        SpringApplication.run(MoodBudsApplication.class, args);
    }
}

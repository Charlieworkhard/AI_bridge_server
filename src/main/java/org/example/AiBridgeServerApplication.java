package org.example;

import org.example.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class AiBridgeServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiBridgeServerApplication.class, args);
    }
}

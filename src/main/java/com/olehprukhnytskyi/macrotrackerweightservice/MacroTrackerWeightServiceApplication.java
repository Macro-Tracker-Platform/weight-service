package com.olehprukhnytskyi.macrotrackerweightservice;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@OpenAPIDefinition(
        info = @Info(
                title = "Weight Service API",
                version = "1.0",
                description = "Microservice for managing user weight records"
        )
)
@EnableJpaRepositories(basePackages = {
        "com.olehprukhnytskyi.repository.jpa",
        "com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa"
})
@EntityScan(basePackages = {
        "com.olehprukhnytskyi.macrotrackerweightservice.model",
        "com.olehprukhnytskyi.model"
})
@SpringBootApplication
public class MacroTrackerWeightServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MacroTrackerWeightServiceApplication.class, args);
    }

}

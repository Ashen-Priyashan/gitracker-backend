package com.gitracker.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gitrackerOpenAPI() {

        return new OpenAPI()

                .info(new Info()
                        .title("Gitracker Backend API")
                        .description("REST API for GitHub profile analytics built with Spring Boot.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Ashen Priyashan")
                                .url("https://github.com/Ashen-Priyashan")
                        )
                )

                .externalDocs(
                        new ExternalDocumentation()
                                .description("Project Repository")
                                .url("https://github.com/Ashen-Priyashan/gitracker-backend")
                );
    }
}
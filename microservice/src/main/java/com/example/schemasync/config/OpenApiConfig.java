package com.example.schemasync.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI schemaSyncOpenAPI() {
        final String apiKeyScheme = "ApiKeyAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("SchemaSync API")
                        .version("1.0.0")
                        .description("""
                                Database schema synchronization microservice.
                                
                                **Features:**
                                - Generate diffs between test and production databases via Liquibase
                                - Validate changesets against a sandbox database
                                - Approve/reject diffs with full audit trail
                                - Apply filtered changesets to target databases
                                - Preview SQL before applying (updateSQL dry-run)
                                - Changeset-level rollback with generated rollback SQL
                                - Jenkins pipeline integration for CI/CD
                                - Merge configuration validation and data transfer
                                """)
                        .contact(new Contact()
                                .name("SchemaSync Team"))
                        .license(new License()
                                .name("MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local development")))
                .addSecurityItem(new SecurityRequirement().addList(apiKeyScheme))
                .schemaRequirement(apiKeyScheme, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-API-KEY")
                        .description("API key passed via X-API-KEY header"));
    }
}

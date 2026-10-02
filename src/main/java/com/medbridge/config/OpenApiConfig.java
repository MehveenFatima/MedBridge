package com.medbridge.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Title and description shown at the top of Swagger UI (/swagger-ui.html). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI medBridgeOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MedBridge - Patient Record Integration Service")
                .version("v1")
                .description("""
                        Ingests patient records from three source systems in three formats \
                        (LAB -> CSV, CLINIC -> JSON, HOSPITAL -> HL7 v2), normalises them into one canonical \
                        schema, validates every field, deduplicates across sources, and logs every rejected \
                        record with its line number and reason.

                        Try it: POST /api/v1/ingest with a file from sample-data/, then \
                        GET /api/v1/batches/{id}/errors and GET /api/v1/patients/{id}/sources."""));
    }
}

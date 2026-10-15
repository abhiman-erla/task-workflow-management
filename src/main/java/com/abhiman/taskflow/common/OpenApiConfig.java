package com.abhiman.taskflow.common;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Adds an "Authorize" button to Swagger UI that sends "Authorization: Bearer <token>". */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "TaskFlow API", version = "0.1.0",
                description = "Task & workflow management with JWT auth and role-based access"),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}

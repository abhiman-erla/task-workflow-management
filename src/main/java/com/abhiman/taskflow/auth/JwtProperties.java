package com.abhiman.taskflow.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Bound from app.jwt.* in application.yml. */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration expiration) {
}

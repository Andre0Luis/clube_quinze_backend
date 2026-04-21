package br.com.clube_quinze.api.config;

import org.springframework.context.annotation.Configuration;

/**
 * Redis cache configuration — caching is currently DISABLED (spring.cache.type=none)
 * to avoid serialization issues with Java Records.
 * 
 * TODO: Re-enable with proper Jackson serializer configuration after validating
 * that all cached DTOs serialize/deserialize correctly.
 */
@Configuration
public class RedisCacheConfig {
}

package com.studybuddy.group.config;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    JwtDecoder jwtDecoder(@Value("${app.auth.jwt-secret}") String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
        }

        var signingKey = new SecretKeySpec(secretBytes, "HmacSHA256");
        var decoder = NimbusJwtDecoder.withSecretKey(signingKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        var issuerValidator = JwtValidators.createDefaultWithIssuer("studybuddy-user-service");
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, this::validateIdentity));
        return decoder;
    }

    private OAuth2TokenValidatorResult validateIdentity(Jwt jwt) {
        try {
            UUID.fromString(jwt.getClaimAsString("uid"));
            if (jwt.getExpiresAt() == null || jwt.getIssuedAt() == null) {
                throw new IllegalArgumentException();
            }
            return OAuth2TokenValidatorResult.success();
        } catch (RuntimeException exception) {
            var error = new OAuth2Error("invalid_token", "Invalid student identity or expiry", null);
            return OAuth2TokenValidatorResult.failure(error);
        }
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("");

        var authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setPrincipalClaimName("uid");
        authenticationConverter.setJwtGrantedAuthoritiesConverter(rolesConverter);

        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(requests -> requests
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/api/groups/**").hasRole("STUDENT")
                .anyRequest().denyAll());
        http.oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter))
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Please log in again\"}");
                }));
        http.exceptionHandling(errors -> errors.accessDeniedHandler((request, response, exception) -> {
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Student access is required\"}");
        }));
        return http.build();
    }
}

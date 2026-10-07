package com.studybuddy.group.config;

import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean JwtDecoder jwtDecoder(@Value("${app.auth.jwt-secret}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
        var decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> identity = jwt -> {
            try {
                UUID.fromString(jwt.getClaimAsString("uid"));
                if (jwt.getExpiresAt() == null || jwt.getIssuedAt() == null) throw new IllegalArgumentException();
                return OAuth2TokenValidatorResult.success();
            } catch (RuntimeException e) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid student identity or expiry", null));
            }
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("studybuddy-user-service"), identity));
        return decoder;
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles"); roles.setAuthorityPrefix("");
        var converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("uid"); converter.setJwtGrantedAuthoritiesConverter(roles);
        return http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/groups/**").hasRole("STUDENT").anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(401); res.setContentType("application/json");
                            res.getWriter().write("{\"message\":\"Please log in again\"}");
                        }))
                .exceptionHandling(e -> e.accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(403); res.setContentType("application/json");
                    res.getWriter().write("{\"message\":\"Student access is required\"}");
                })).build();
    }
}

package com.studybuddy.user.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
	private final SecretKey jwtKey;
	public SecurityConfiguration(@Value("${app.auth.jwt-secret:}") String secret) {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32)
			throw new IllegalStateException("JWT_SECRET must contain at least 32 characters");
		this.jwtKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}
	@Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
	@Bean JwtEncoder jwtEncoder() { return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey)); }
	@Bean JwtDecoder jwtDecoder() { return NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build(); }
	@Bean JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(jwt -> jwt.getClaimAsStringList("roles").stream()
				.map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role)).toList());
		return converter;
	}
	@Bean SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
		return http.csrf(csrf -> csrf.disable()).cors(Customizer.withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
					.requestMatchers("/api/auth/login", "/actuator/health").permitAll()
					.requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
					.requestMatchers(HttpMethod.GET, "/api/courses/**").permitAll()
					.requestMatchers(HttpMethod.GET, "/api/students", "/api/students/*").permitAll()
					.requestMatchers(HttpMethod.GET, "/api/students/internal/**").permitAll()
					.requestMatchers("/api/admin/**").hasRole("ADMIN")
					.requestMatchers(HttpMethod.POST, "/api/students/**").hasRole("ADMIN")
					.requestMatchers(HttpMethod.POST, "/api/courses/**").hasRole("ADMIN")
					.requestMatchers(HttpMethod.PUT, "/api/courses/**").hasRole("ADMIN")
					.requestMatchers("/api/students/**").hasAnyRole("STUDENT", "ADMIN")
					.anyRequest().authenticated())
				.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
				.build();
	}
	@Bean CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		cors.setAllowCredentials(false);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", cors);
		return source;
	}
}

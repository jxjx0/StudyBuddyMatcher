package com.studybuddy.user.service;

import com.studybuddy.user.model.SystemAdministrator;
import com.studybuddy.user.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class JwtService {
	private final JwtEncoder encoder;
	private final long ttlMinutes;
	public JwtService(JwtEncoder encoder, @Value("${app.auth.jwt-ttl-minutes:60}") long ttlMinutes) {
		this.encoder = encoder;
		this.ttlMinutes = ttlMinutes;
	}
	public String issue(User user) {
		Instant now = Instant.now();
		String role = user instanceof SystemAdministrator ? "ADMIN" : "STUDENT";
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("studybuddy-user-service")
				.issuedAt(now).expiresAt(now.plus(ttlMinutes, ChronoUnit.MINUTES))
				.subject(user.getEmail()).claim("uid", user.getUserId())
				.claim("roles", List.of("ROLE_" + role)).build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}
}

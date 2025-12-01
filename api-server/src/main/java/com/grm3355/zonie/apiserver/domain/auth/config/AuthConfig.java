package com.grm3355.zonie.apiserver.domain.auth.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.grm3355.zonie.apiserver.domain.auth.domain.AuthProvider;
import com.grm3355.zonie.apiserver.domain.auth.domain.OAuth2Client;
import com.grm3355.zonie.apiserver.domain.auth.domain.OAuth2Clients;
import com.grm3355.zonie.apiserver.domain.auth.infra.JwtAuthProvider;

@Configuration
public class AuthConfig {

	private static final long EXPIRATION_MINUTES = 360;

	private final String secretKey;

	public AuthConfig(@Value("${jwt.secret}") String secretKey) {
		this.secretKey = secretKey;
	}

	@Bean
	public AuthProvider jwtAuthProvider() {
		return new JwtAuthProvider(secretKey, EXPIRATION_MINUTES);
	}

	@Bean
	public OAuth2Clients oAuth2Clients(List<OAuth2Client> oAuth2Clients) {
		return OAuth2Clients.builder()
			.addAll(oAuth2Clients)
			.build();
	}
}

package com.grm3355.zonie.apiserver.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginResponse {

	@Schema(description = "Access Token", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1...")
	String accessToken;

	@Schema(description = "Refresh Token", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ...")
	String refreshToken;

	public LoginResponse(String accessToken, String refreshToken) {
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
	}
}

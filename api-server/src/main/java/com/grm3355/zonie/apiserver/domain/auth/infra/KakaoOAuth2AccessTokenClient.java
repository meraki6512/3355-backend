package com.grm3355.zonie.apiserver.domain.auth.infra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.grm3355.zonie.apiserver.domain.auth.dto.KakaoAccessTokenResponse;

@Component
public class KakaoOAuth2AccessTokenClient {

	private static final String ACCESS_TOKEN_URL = "https://kauth.kakao.com/oauth/token";

	private final RestTemplate restTemplate;
	private final String grantType;
	private final String clientId;
	private final String redirectUri;

	public KakaoOAuth2AccessTokenClient(
		@Value("${spring.oauth2.client.registration.kakao.authorization-grant-type}") String grantType,
		@Value("${spring.oauth2.client.registration.kakao.client-id}") String clientId,
		@Value("${spring.oauth2.client.registration.kakao.redirect-uri}") String redirectUri,
		RestTemplateBuilder restTemplateBuilder
	) {
		this.grantType = grantType;
		this.clientId = clientId;
		this.redirectUri = redirectUri;
		this.restTemplate = restTemplateBuilder
			.errorHandler(new KakaoOAuth2AccessTokenErrorHandler())
			.build();
	}

	public String getAccessToken(String code) {
		HttpHeaders headers = getAccessTokenHeaders(code);
		return requestAccessToken(headers);
	}

	private HttpHeaders getAccessTokenHeaders(String code) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		headers.set("grant_type", grantType);
		headers.set("client_id", clientId);
		headers.set("redirect_uri", redirectUri);
		headers.set("code", code);
		return headers;
	}

	private String requestAccessToken(HttpHeaders headers) {
		KakaoAccessTokenResponse response = restTemplate.postForEntity(ACCESS_TOKEN_URL, headers,
			KakaoAccessTokenResponse.class).getBody();
		return response != null ? response.accessToken() : null;
	}
}

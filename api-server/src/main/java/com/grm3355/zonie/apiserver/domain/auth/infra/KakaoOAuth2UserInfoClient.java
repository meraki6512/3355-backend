package com.grm3355.zonie.apiserver.domain.auth.infra;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.grm3355.zonie.apiserver.domain.auth.domain.UserInfo;
import com.grm3355.zonie.apiserver.domain.auth.dto.KakaoUserInfo;

@Component
public class KakaoOAuth2UserInfoClient {

	private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";
	private final RestTemplate restTemplate;

	public KakaoOAuth2UserInfoClient(RestTemplateBuilder restTemplateBuilder) {
		this.restTemplate = restTemplateBuilder
			.errorHandler(new KakaoOAuth2UserInfoErrorHandler())
			.build();
	}

	public UserInfo getUserInfo(String accessToken) {
		HttpHeaders headers = getUserInfoHeaders(accessToken);
		return requestUserInfo(headers);
	}

	private HttpHeaders getUserInfoHeaders(String accessToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		headers.setBearerAuth(accessToken);
		return headers;
	}

	private UserInfo requestUserInfo(HttpHeaders headers) {
		KakaoUserInfo kakaoUserInfo = restTemplate.postForEntity(USER_INFO_URL, new HttpEntity<>(headers),
				KakaoUserInfo.class)
			.getBody();
		return kakaoUserInfo != null ? kakaoUserInfo.toUserInfo() : null;
	}
}

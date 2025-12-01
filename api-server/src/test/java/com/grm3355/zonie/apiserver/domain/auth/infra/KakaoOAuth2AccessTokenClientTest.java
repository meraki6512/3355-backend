package com.grm3355.zonie.apiserver.domain.auth.infra;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.HttpClientErrorException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grm3355.zonie.apiserver.domain.auth.dto.KakaoAccessTokenResponse;
import com.grm3355.zonie.apiserver.domain.auth.infra.KakaoOAuth2AccessTokenErrorHandler.KakaoOAuth2ErrorResponse;

@DisplayNameGeneration(ReplaceUnderscores.class)
@SuppressWarnings("NonAsciiCharacters")
@RestClientTest(KakaoOAuth2AccessTokenClient.class)
class KakaoOAuth2AccessTokenClientTest {

	private static final String ACCESS_TOKEN_URL = "https://kauth.kakao.com/oauth/token";

	@Autowired
	KakaoOAuth2AccessTokenClient kakaoOAuth2AccessTokenClient;

	@Autowired
	MockRestServiceServer mockServer;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	@DisplayName("전송한_코드가_잘못됐다면_예외")
	void failWhenCodeIsInvalid() throws JsonProcessingException {
		KakaoOAuth2ErrorResponse expected = new KakaoOAuth2ErrorResponse("error", "description", "KOE320");
		mockServer.expect(requestTo(ACCESS_TOKEN_URL))
			.andRespond(MockRestResponseCreators.withBadRequest()
				.contentType(MediaType.APPLICATION_JSON)
				.body(objectMapper.writeValueAsString(expected)));

		assertThatThrownBy(() -> kakaoOAuth2AccessTokenClient.getAccessToken("code"))
			.isInstanceOf(HttpClientErrorException.class);
	}

	@Test
	@DisplayName("예상치못한_에러인_경우_서버_예외")
	void failWhenUnexpectedError() throws JsonProcessingException {
		KakaoOAuth2ErrorResponse expected = new KakaoOAuth2ErrorResponse("error", "description", "any");
		mockServer.expect(requestTo(ACCESS_TOKEN_URL))
			.andRespond(MockRestResponseCreators.withBadRequest()
				.contentType(MediaType.APPLICATION_JSON)
				.body(objectMapper.writeValueAsString(expected)));

		assertThatThrownBy(() -> kakaoOAuth2AccessTokenClient.getAccessToken("code"))
			.isInstanceOf(HttpClientErrorException.class);
	}

	@Test
	@DisplayName("카카오 성공")
	void kakaoSuccess() throws JsonProcessingException {
		KakaoAccessTokenResponse expected = new KakaoAccessTokenResponse("tokenType", "accessToken",
			100, "refreshToken", 50);
		mockServer.expect(requestTo(ACCESS_TOKEN_URL))
			.andRespond(MockRestResponseCreators.withSuccess()
				.contentType(MediaType.APPLICATION_JSON)
				.body(objectMapper.writeValueAsString(expected)));
		String actual = kakaoOAuth2AccessTokenClient.getAccessToken("code");

		assertThat(actual).isEqualTo(expected.accessToken());
	}
}

package com.grm3355.zonie.apiserver.domain.auth.infra;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.SoftAssertions.*;
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
import com.grm3355.zonie.apiserver.domain.auth.domain.UserInfo;
import com.grm3355.zonie.apiserver.domain.auth.dto.KakaoUserInfo;
import com.grm3355.zonie.apiserver.domain.auth.dto.KakaoUserInfo.KakaoAccount;
import com.grm3355.zonie.apiserver.domain.auth.dto.KakaoUserInfo.KakaoAccount.Profile;
import com.grm3355.zonie.commonlib.global.enums.ProviderType;

@DisplayNameGeneration(ReplaceUnderscores.class)
@RestClientTest(KakaoOAuth2UserInfoClient.class)
class KakaoOAuth2UserInfoClientTest {

	private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

	@Autowired
	KakaoOAuth2UserInfoClient kakaoOAuth2UserInfoClient;

	@Autowired
	MockRestServiceServer mockServer;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	@DisplayName("예상치못한_에러인_경우_서버_예외")
	void failWhenUnexpectedError() {
		mockServer.expect(requestTo(USER_INFO_URL))
			.andRespond(MockRestResponseCreators.withBadRequest()
				.contentType(MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> kakaoOAuth2UserInfoClient.getUserInfo("accessToken"))
			.isInstanceOf(HttpClientErrorException.class);
	}

	@Test
	@DisplayName("카카오 유저 인포 성공")
	void kakaoUserInfoSuccess() throws JsonProcessingException {
		KakaoUserInfo expected = new KakaoUserInfo("id",
			new KakaoAccount("email", new Profile("nickname", "imageUrl")));
		mockServer.expect(requestTo(USER_INFO_URL))
			.andRespond(MockRestResponseCreators.withSuccess()
				.body(objectMapper.writeValueAsString(expected))
				.contentType(MediaType.APPLICATION_JSON));

		UserInfo actual = kakaoOAuth2UserInfoClient.getUserInfo("accessToken");

		assertSoftly(softly -> {
			softly.assertThat(actual.getProviderType()).isEqualTo(ProviderType.KAKAO);
			softly.assertThat(actual.getSocialId()).isEqualTo(expected.id());
			softly.assertThat(actual.getNickname()).isEqualTo(expected.kakaoAccount().profile().nickname());
			softly.assertThat(actual.getProfileImage())
				.isEqualTo(expected.kakaoAccount().profile().thumbnailImageUrl());
		});
	}
}

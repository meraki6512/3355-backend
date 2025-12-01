package com.grm3355.zonie.apiserver.domain.auth.infra;

import org.springframework.stereotype.Component;

import com.grm3355.zonie.apiserver.domain.auth.domain.OAuth2Client;
import com.grm3355.zonie.apiserver.domain.auth.domain.UserInfo;
import com.grm3355.zonie.commonlib.global.enums.ProviderType;

@Component
public class KakaoOAuth2Client implements OAuth2Client {

	private final KakaoOAuth2AccessTokenClient accessTokenClient;
	private final KakaoOAuth2UserInfoClient userInfoClient;

	public KakaoOAuth2Client(KakaoOAuth2AccessTokenClient accessTokenClient, KakaoOAuth2UserInfoClient userInfoClient) {
		this.accessTokenClient = accessTokenClient;
		this.userInfoClient = userInfoClient;
	}

	@Override
	public String getAccessToken(String code) {
		return accessTokenClient.getAccessToken(code);
	}

	@Override
	public UserInfo getUserInfo(String accessToken) {
		return userInfoClient.getUserInfo(accessToken);
	}

	@Override
	public ProviderType getProviderType() {
		return ProviderType.KAKAO;
	}
}

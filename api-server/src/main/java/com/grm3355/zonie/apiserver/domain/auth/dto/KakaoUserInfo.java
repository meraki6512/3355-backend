package com.grm3355.zonie.apiserver.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.grm3355.zonie.apiserver.domain.auth.domain.UserInfo;
import com.grm3355.zonie.commonlib.global.enums.ProviderType;

public record KakaoUserInfo(
	String id,
	@JsonProperty("kakao_account") KakaoAccount kakaoAccount
) {
	public UserInfo toUserInfo() {

		// profile 객체 가져오기 (null-safe)
		KakaoAccount.Profile profile = kakaoAccount.profile();

		String nickname = (profile != null && profile.nickname() != null && !profile.nickname().isBlank())
			? profile.nickname()
			: "익명"; // 기본값

		String profileImage =
			(profile != null && profile.thumbnailImageUrl() != null && !profile.thumbnailImageUrl().isBlank())
				? profile.thumbnailImageUrl()
				: null; // 필요하면 기본 이미지 URL 사용 가능

		String email = (kakaoAccount.email() != null && !kakaoAccount.email().isBlank())
			? kakaoAccount.email() : null; // 이메일도 선택적 필드

		return UserInfo.builder()
			.socialId(id)
			.providerType(ProviderType.KAKAO)
			.email(email)
			.nickname(nickname)
			.profileImage(profileImage).build();
	}

	public record KakaoAccount(
		String email,
		Profile profile
	) {

		public record Profile(
			String nickname,
			@JsonProperty("thumbnail_image_url") String thumbnailImageUrl
		) {

		}
	}
}

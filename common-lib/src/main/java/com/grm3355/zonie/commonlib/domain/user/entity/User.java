package com.grm3355.zonie.commonlib.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.grm3355.zonie.commonlib.global.entity.BaseTimeEntity;
import com.grm3355.zonie.commonlib.global.enums.ProviderType;
import com.grm3355.zonie.commonlib.global.enums.Role;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
@Setter
public class User extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, unique = true, length = 100)
	@NotBlank(message = "사용자아이디는 정보는 필수 입력 값입니다.")
	private String userId;

	@Size(max = 100)
	@Column(name = "password")
	private String password;

	// USER, ADMIN - 카카오회원은 USER
	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20)
	private Role role;

	// 닉네임
	@Column(name = "profile_nickname")
	private String profileNickName;

	// 이메일
	@Column(name = "account_email")
	private String accountEmail;

	// 프로필 이미지
	@Column(name = "profile_image")
	private String profileImage;

	// 로그인 제공자 (KAKAO, GOOGLE, NAVER, APPLE 등)
	@Enumerated(EnumType.STRING)
	@Column(name = "provider_type")
	private ProviderType providerType;

	// SNS가 제공하는 고유 ID (sub, id 등)
	@Column(name = "social_id")
	private String socialId;

	//SocialId 해시값 가입여부 비교할때 조회하는 칼럼
	@Column(name = "social_id_hash")
	private String socialIdHash; // 조회용 SHA256 해시, 인덱스 가능

	public void updateEmail(String email) {
		this.accountEmail = email;
	}
}

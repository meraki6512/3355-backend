package com.grm3355.zonie.apiserver.global.jwt;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.grm3355.zonie.commonlib.domain.user.entity.User;

import lombok.Getter;

public class UserDetailsImpl implements UserDetails {
	@Getter
	private final String userId;
	private final String username; // email

	@JsonIgnore
	private final String password;

	private final Collection<? extends GrantedAuthority> authorities;

	private final boolean isEnabled;
	private final boolean isAccountNonLocked;

	public UserDetailsImpl(String userId, String username, String password,
		Collection<? extends GrantedAuthority> authorities,
		boolean isEnabled, boolean isAccountNonLocked) {
		this.userId = userId;
		this.username = username;
		this.password = password;
		this.authorities = authorities;
		this.isEnabled = isEnabled;
		this.isAccountNonLocked = isAccountNonLocked;
	}

	/**
	 * Member 엔티티를 기반으로 Spring Security의 UserDetails 객체를 생성한다.
	 * Member의 역할(Role)은 권한(GrantedAuthority)으로, 상태(MemberStatus)는 계정 활성화/잠금 상태로 매핑된다.
	 */
	public static UserDetailsImpl build(User user) {
		List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

		return new UserDetailsImpl(
			user.getUserId(),
			user.getUserId(),
			user.getPassword(),
			authorities,
			true, // 계정 활성 상태 (ACTIVE일 때만 true)
			true // 계정 잠금 상태 (LOCKED가 아닐 때만 true)
		);
	}

	// 토큰 Payload 정보로 UserDetails 객체를 생성하는 정적 팩토리 메서드
	public static UserDetailsImpl buildFromToken(String userId, String roleName, String password) {
		List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + roleName));

		return new UserDetailsImpl(
			userId,
			userId, // username (userId 사용)
			password,
			authorities,
			true, // isEnabled (토큰이 유효하면 활성으로 간주)
			true  // isAccountNonLocked (토큰이 유효하면 잠금되지 않은 것으로 간주)
		);
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return authorities;
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true; // 요구사항에 따라 추가 구현 가능
	}

	@Override
	public boolean isAccountNonLocked() {
		return this.isAccountNonLocked;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true; // 요구사항에 따라 추가 구현 가능
	}

	@Override
	public boolean isEnabled() {
		return this.isEnabled;
	}
}

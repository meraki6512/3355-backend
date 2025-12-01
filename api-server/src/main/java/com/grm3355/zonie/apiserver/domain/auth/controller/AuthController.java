package com.grm3355.zonie.apiserver.domain.auth.controller;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.grm3355.zonie.apiserver.domain.auth.dto.AccessTokenResponse;
import com.grm3355.zonie.apiserver.domain.auth.dto.LoginRequest;
import com.grm3355.zonie.apiserver.domain.auth.dto.LoginResponse;
import com.grm3355.zonie.apiserver.domain.auth.service.AuthService;
import com.grm3355.zonie.apiserver.domain.auth.service.RedisTokenService;
import com.grm3355.zonie.apiserver.domain.auth.util.CookieProperties;
import com.grm3355.zonie.apiserver.global.jwt.UserDetailsImpl;
import com.grm3355.zonie.apiserver.global.swagger.ApiError400;
import com.grm3355.zonie.apiserver.global.swagger.ApiError405;
import com.grm3355.zonie.apiserver.global.swagger.ApiError415;
import com.grm3355.zonie.apiserver.global.swagger.ApiError429;
import com.grm3355.zonie.commonlib.global.enums.ProviderType;
import com.grm3355.zonie.commonlib.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@Tag(name = "Auth", description = "사용자 토큰 발급")
@RequestMapping("/api/auth")
public class AuthController {
	private final AuthService authService;
	private final RedisTokenService redisTokenService;
	private final CookieProperties cookieProperties;

	public AuthController(AuthService authService, RedisTokenService redisTokenService,
		CookieProperties cookieProperties) {
		this.authService = authService;
		this.redisTokenService = redisTokenService;
		this.cookieProperties = cookieProperties;
	}

	// 현재는 사용안하므로 주석처리
	// 해당url은 지금은 사용할 일 없지만, 확장성을 위해서 보관한다.
	// 개발할때 업스케일링하는 과정에서나온 url
	@Deprecated
	@Hidden
	@PostMapping("/oauth2")
	@Operation(summary = "로그인 (deprecated)", description = "로그인 처리")
	public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
		LoginResponse response = authService.login(request);
		return ResponseEntity.ok()
			.body(response);
	}

	@Hidden
	@Operation(summary = "카카오 로그인 사용자 토큰 발급", description = "사용자 로그인후 AccessToken, RefreshToken 발급합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "200",
			description = "카카오 로그인 사용자 토큰 발급",
			content = @Content(
				mediaType = "application/json",
				schema = @Schema(implementation = LoginResponse.class)
			)
		)
	})
	@ApiError400
	@ApiError405
	@ApiError415
	@ApiError429
	@GetMapping("/kakao/callback")
	public void loginWithKakao(@RequestParam("code") String code,
		HttpServletResponse response, @RequestParam("state") String returnUrl) throws IOException {

		LoginResponse loginResponse = authService.login(new LoginRequest(ProviderType.KAKAO, code));

		String accessToken = loginResponse.getAccessToken();
		String refreshToken = loginResponse.getRefreshToken();

		log.info("samSite3 비교 {} ", cookieProperties.getSameSite());
		log.info("domain3 비교 {}", cookieProperties.getDomain());

		//HttpOnly 쿠키에 리프레시 토큰 저장
		ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
			.httpOnly(true)
			.secure(cookieProperties.isSecure()) // 로컬 개발 환경이라 false, https면 true
			.path("/")
			.maxAge(cookieProperties.getMaxAge()) // 7일
			.sameSite(cookieProperties.getSameSite())
			.domain(cookieProperties.getDomain())
			.build();
		response.addHeader("Set-Cookie", cookie.toString());

		//새창 방식 프론트에는 액세스 토큰만 전달
		//차후에 사용가능하므로 우선 보관한다.
		/*
		String html =
			"<!DOCTYPE html><html><body>" +
				"<script>" +
				"  if (!window.posted) {" +
				"    window.opener.postMessage({" +
				"      accessToken: '" + accessToken + "'" +
				"    }, '" + returnUrl + "');" +
				"    window.posted = true;" +
				"    window.close();" +
				"  }" +
				"</script>" +
				"</body></html>";
		//return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
		*/

		//현재창 방식
		log.info("사용자 토큰 내보내기 ====> {}", accessToken.substring(0, 10));
		String redirectUrl = returnUrl + "?accessToken=" + accessToken;
		response.sendRedirect(redirectUrl);  // 제거해서 프론트로 돌려보냄
	}

	@Operation(summary = "리프레시 토큰 재발급", description = "사용자 토큰 만료시 AccessToken, RefreshToken 재발급합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "200",
			description = "리프레시 토큰 재발급",
			content = @Content(
				mediaType = "application/json",
				schema = @Schema(implementation = LoginResponse.class)
			)
		)
	})
	@ApiError400
	@ApiError405
	@ApiError415
	@ApiError429
	@PostMapping("/refresh")
	@Transactional
	public ResponseEntity<?> refresh(
		@CookieValue(name = "refreshToken", required = false) String refreshToken,
		HttpServletResponse response) {

		if (refreshToken == null || !redisTokenService.validateRefreshToken(refreshToken)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		//토큰 재발급
		LoginResponse loginResponse = authService.refreshAccessToken(refreshToken);

		// HttpOnly 쿠키로 새 리프레시 토큰 발급
		ResponseCookie cookie = ResponseCookie.from("refreshToken", loginResponse.getRefreshToken())
			.httpOnly(true)
			.secure(cookieProperties.isSecure()) // HTTPS 환경이면 true
			.path("/")
			.maxAge(cookieProperties.getMaxAge()) // 7일
			.sameSite(cookieProperties.getSameSite())
			.domain(cookieProperties.getDomain())
			.build();
		response.addHeader("Set-Cookie", cookie.toString());

		AccessTokenResponse accessTokenResponse = new AccessTokenResponse(loginResponse.getAccessToken());
		return ResponseEntity.ok().body(ApiResponse.success(accessTokenResponse));
	}

	@Operation(summary = "로그아웃", description = "서버에 저장된 Refresh 토큰을 삭제하여 로그아웃 처리합니다. 클라이언트 측에서도 저장된 액세스토큰, 리프레시 토큰을 모두 삭제해야 합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "로그아웃 성공",
			content = @Content(mediaType = "application/json"
			))
	})
	@ApiError400
	@ApiError405
	@ApiError415
	@ApiError429
	@PreAuthorize("isAuthenticated()")
	@SecurityRequirement(name = "Authorization")
	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Void>> logout(HttpServletResponse response,
		@AuthenticationPrincipal UserDetailsImpl userDetails) {
		//200 응답 나오면 프론트엔드에서 액세스토큰, 리프레시 토큰 삭제

		//Redis에서 리프레시 토큰 삭제
		redisTokenService.deleteByToken(userDetails.getUserId());

		//리프레시 토큰 값 제거
		ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
			.httpOnly(true)
			.secure(cookieProperties.isSecure()) // 로컬 환경
			.path("/")
			.maxAge(0)
			.sameSite(cookieProperties.getSameSite())
			.domain(cookieProperties.getDomain())
			.build();
		response.addHeader("Set-Cookie", cookie.toString());

		log.info("사용자 로그아웃 성공");
		return ResponseEntity.noContent().build();
	}
}


package com.grm3355.zonie.apiserver.domain.auth.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grm3355.zonie.apiserver.domain.auth.domain.OAuth2Client;
import com.grm3355.zonie.apiserver.domain.auth.domain.OAuth2Clients;
import com.grm3355.zonie.apiserver.domain.auth.domain.UserInfo;
import com.grm3355.zonie.apiserver.domain.auth.dto.AuthResponse;
import com.grm3355.zonie.apiserver.domain.auth.dto.LocationDto;
import com.grm3355.zonie.apiserver.domain.auth.dto.LoginRequest;
import com.grm3355.zonie.apiserver.domain.auth.dto.LoginResponse;
import com.grm3355.zonie.apiserver.domain.auth.dto.UserTokenDto;
import com.grm3355.zonie.apiserver.domain.auth.util.AESUtil;
import com.grm3355.zonie.apiserver.domain.auth.util.HashUtil;
import com.grm3355.zonie.apiserver.global.jwt.UserDetailsImpl;
import com.grm3355.zonie.apiserver.global.jwt.UserDetailsServiceImpl;
import com.grm3355.zonie.commonlib.domain.user.entity.User;
import com.grm3355.zonie.commonlib.domain.user.repository.UserRepository;
import com.grm3355.zonie.commonlib.global.enums.Role;
import com.grm3355.zonie.commonlib.global.exception.BusinessException;
import com.grm3355.zonie.commonlib.global.exception.ErrorCode;
import com.grm3355.zonie.commonlib.global.util.JwtTokenProvider;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

	private static final String PRE_FIX = "";
	private final JwtTokenProvider jwtTokenProvider;
	private final AuthenticationManager authenticationManager;
	private final UserRepository userRepository;
	private final RedisTokenService redisTokenService;
	private final PasswordEncoder passwordEncoder;
	private final OAuth2Clients oAuth2Clients;
	private final UserDetailsServiceImpl userDetailsService;
	private final AESUtil aesUtil;

	/**
	 * [TestManagement] 테스트용 유저 ID로 즉시 토큰을 발급합니다.
	 *
	 * @param userId      DB에 존재하는 userId (e.g., "test_user_01_kakao")
	 * @param nonExpiring true 시 10년 만기 토큰 발급
	 * @return LoginResponse
	 */
	@Transactional
	public LoginResponse generateTestToken(String userId, boolean nonExpiring) {
		// 1. DB에서 테스트 유저 조회
		User user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "테스트 사용자를 찾을 수 없습니다: " + userId));

		// 2. UserDetails 생성 (refreshAccessToken에서 사용하는 로직과 동일)
		UserDetailsImpl userDetails = userDetailsService.getUserDetailsByEmail(user.getUserId());

		// 3. 비만료 토큰 처리
		if (nonExpiring) {
			long originalAccessTtl = jwtTokenProvider.getAccessTokenExpirationTime();
			long originalRefreshTtl = jwtTokenProvider.getRefreshTokenExpirationTime();
			long originalRedisTtl = redisTokenService.getRefreshTokenExpirationTime();

			// 10년짜리 비만료 토큰 (ms)
			long tenYearsMs = 315360000000L;

			try {
				log.warn("[TEST-MGMT] 비만료 토큰 발급 요청. 토큰 TTL을 임시 변경합니다.");
				jwtTokenProvider.setAccessTokenExpirationTime(tenYearsMs);
				jwtTokenProvider.setRefreshTokenExpirationTime(tenYearsMs);
				redisTokenService.setRefreshTokenExpirationTime(tenYearsMs);

				// 토큰 생성
				String accessToken = jwtTokenProvider.createAccessToken(userDetails.getUsername(), user.getRole());
				String refreshToken = redisTokenService.createRefreshToken(userDetails.getUsername());

				return new LoginResponse(accessToken, refreshToken);

			} finally {
				// 토큰 TTL 원상 복구
				jwtTokenProvider.setAccessTokenExpirationTime(originalAccessTtl);
				jwtTokenProvider.setRefreshTokenExpirationTime(originalRefreshTtl);
				redisTokenService.setRefreshTokenExpirationTime(originalRedisTtl);
				log.warn("[TEST-MGMT] 토큰 TTL을 원상 복구합니다.");
			}
		}

		// 4. 표준 만료 토큰 (기존 로직 재사용)
		return generateNewTokens(userDetails);
	}

	@Transactional
	public AuthResponse register(LocationDto locationDto) {

		//uuid 생성
		String userId = PRE_FIX + UUID.randomUUID();
		double lat = locationDto.getLat();
		double lon = locationDto.getLon();

		try {
			// 임시 테스트 이메일 생성 (userId 기반)
			String testEmail = userId + "@test-guest.com";
			String encryptedEmail = aesUtil.encrypt(testEmail); // 이메일 암호화

			//아이디저장
			String password = passwordEncoder.encode(userId);
			User user = User.builder()
				.userId(userId)
				.password(password)
				.accountEmail(encryptedEmail) // 암호화된 이메일 추가
				.role(Role.USER).build();
			userRepository.save(user);
		} catch (Exception e) {
			// 암호화 실패 시 예외 처리
			throw new RuntimeException("Test user email encryption failed", e);
		}

		//사용자정보
		UserTokenDto userTokenDto = UserTokenDto.builder()
			.userId(userId).lat(lat).lon(lon).build();

		//아이디 저장후 인증정보 authentication  정보 가져오기
		Authentication authentication = authenticationManager.authenticate(
			new UsernamePasswordAuthenticationToken(userId, userId)
		);
		UserDetailsImpl userDetails = (UserDetailsImpl)authentication.getPrincipal();

		// --- 로그인 성공 ---
		log.info("사용자 로그인 성공");

		//토큰 생성
		return generateTokens(userDetails, userTokenDto);
	}

	public AuthResponse generateTokens(UserDetailsImpl userDetails, UserTokenDto userTokenDto) {
		// 현재 시스템은 사용자당 단일 권한을 가정하므로, 첫 번째 권한을 가져와 사용합니다.
		// 향후 다중 권한을 지원하려면 이 로직의 수정이 필요합니다.

		String roleName = userDetails.getAuthorities().stream()
			.findFirst()
			.map(GrantedAuthority::getAuthority)
			.orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "사용자 권한 정보를 찾을 수 없습니다."));

		// "ROLE_GUEST" -> "GUEST"
		String roleEnumName = roleName.startsWith("ROLE_") ? roleName.substring(5) : roleName;

		//액세스 토큰 생성(JWT) - 클라이언트가 저장
		String accessToken = jwtTokenProvider.createAccessToken(userDetails.getUsername(), Role.valueOf(roleEnumName));

		return new AuthResponse(accessToken, null);
	}

	@Transactional
	public LoginResponse login(LoginRequest request) {
		UserInfo userInfo = getUserInfo(request);
		String socialIdHash = HashUtil.sha256(userInfo.getSocialId());
		User user = userRepository.findBySocialIdHashAndProviderTypeAndDeletedAtIsNull(socialIdHash,
				userInfo.getProviderType())
			.orElseGet(() -> {
				try {
					return signUp(userInfo);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});

		//return new LoginResponse(jwtTokenProvider.createAccessToken(user.getUserId(), user.getRole()),
		//	userInfo.nickname());

		//아이디 저장후 인증정보 authentication 정보 가져오기
		String randomDateId = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
			+ (int)(Math.random() * 10000); // 0~9999
		System.out.println(randomDateId); // 예: 202511120384

		//액세스 토큰 생성(JWT) - 클라이언트가 저장
		String accessToken = jwtTokenProvider.createAccessToken(user.getUserId(), user.getRole());
		String refreshToken = redisTokenService.createRefreshToken(user.getUserId());
		return new LoginResponse(accessToken, refreshToken);
	}

	private UserInfo getUserInfo(LoginRequest request) {
		OAuth2Client oAuth2Client = oAuth2Clients.getClient(request.providerType());
		String accessToken = oAuth2Client.getAccessToken(request.code());
		return oAuth2Client.getUserInfo(accessToken);
	}

	private User signUp(UserInfo userInfo) throws Exception {
		//암호화
		String socialIdHash = HashUtil.sha256(userInfo.getSocialId());
		userInfo.setEmail(aesUtil.encrypt(userInfo.getEmail()));
		userInfo.setSocialId(aesUtil.encrypt(userInfo.getSocialId()));

		userInfo.setSocialIdHash(socialIdHash);

		//저장
		return userRepository.save(userInfo.toUser());
	}

	public LoginResponse generateNewTokens(UserDetailsImpl userDetails) {
		// 현재 시스템은 사용자당 단일 권한을 가정하므로, 첫 번째 권한을 가져와 사용합니다.
		// 향후 다중 권한을 지원하려면 이 로직의 수정이 필요합니다.

		String roleName = userDetails.getAuthorities().stream()
			.findFirst()
			.map(GrantedAuthority::getAuthority)
			.orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "사용자 권한 정보를 찾을 수 없습니다."));

		// "ROLE_GUEST" -> "GUEST"
		String roleEnumName = roleName.startsWith("ROLE_") ? roleName.substring(5) : roleName;

		//액세스 토큰 생성(JWT) - 클라이언트가 저장
		String accessToken = jwtTokenProvider.createAccessToken(userDetails.getUsername(), Role.valueOf(roleEnumName));
		String refreshToken = redisTokenService.createRefreshToken(userDetails.getUsername());

		//위치 토큰 생성 - 실시간 저장을 위해서 Redis에만 저장
		//redisTokenService.generateLocationToken(userTokenDto);

		return new LoginResponse(accessToken, refreshToken);
	}

	/**
	 * 만료된 Access Token을 새로운 토큰으로 갱신합니다.
	 */
	@Transactional
	public LoginResponse refreshAccessToken(String requestRefreshToken) {
		// 1. JWT 유효성 검증 (만료 여부, 서명 유효성 등)
		try {
			jwtTokenProvider.validateToken(requestRefreshToken);
		} catch (ExpiredJwtException e) {
			log.warn("리프레시 토큰이 만료되었습니다: {}", requestRefreshToken);
			// 만료된 토큰은 Redis에서도 삭제
			redisTokenService.deleteByToken(requestRefreshToken);
			throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "리프레시 토큰이 만료되었습니다.");
		} catch (JwtException | IllegalArgumentException e) {
			log.warn("유효하지 않은 JWT 토큰입니다: {}", e.getMessage());
			throw new BusinessException(ErrorCode.TOKEN_INVALID, "유효하지 않은 리프레시 토큰입니다.");
		}

		// 2. Redis에서 리프레시 토큰 정보 조회
		RedisTokenService.RefreshTokenInfo refreshTokenInfo = redisTokenService.findByToken(requestRefreshToken)
			.orElseThrow(() -> {
				log.warn("Redis에서 리프레시 토큰을 찾을 수 없거나 이미 사용된 토큰입니다: {}", requestRefreshToken);
				// Redis에 없는 토큰은 유효하지 않거나 이미 사용된 것으로 간주
				return new BusinessException(ErrorCode.TOKEN_INVALID, "유효하지 않거나 이미 사용된 리프레시 토큰입니다.");
			});

		if (refreshTokenInfo.used()) {
			log.warn("사용자 {}의 리프레시 토큰 재사용이 감지되었습니다. 모든 토큰을 무효화합니다.", refreshTokenInfo.userId());
			redisTokenService.deleteAllTokensForUser(refreshTokenInfo.userId());
			throw new BusinessException(ErrorCode.TOKEN_INVALID, "리프레시 토큰이 이미 사용되었습니다. 모든 세션이 종료됩니다.");
		}

		// 3. 토큰에서 이메일 추출 및 사용자 정보 로드
		String userId = jwtTokenProvider.getUserIdFromToken(requestRefreshToken);
		UserDetailsImpl userDetails = userDetailsService.getUserDetailsByEmail(userId);

		// 4. 토큰 정보와 사용자 정보 일치 여부 확인
		if (!userId.equals(refreshTokenInfo.userId())) {
			log.warn("리프레시 토큰의 아이디가 일치하지 않습니다. 토큰 아이디: {}, 사용자 아이디: {}", refreshTokenInfo.userId(), userId);
			// 불일치 시 해당 토큰 삭제 (보안 강화)
			redisTokenService.deleteByToken(requestRefreshToken);
			throw new BusinessException(ErrorCode.TOKEN_INVALID, "리프레시 토큰의 사용자 정보가 일치하지 않습니다.");
		}

		// 5. 기존 리프레시 토큰 무효화 (토큰 로테이션)
		redisTokenService.deleteByToken(requestRefreshToken);
		log.info("사용자 {}의 기존 리프레시 토큰을 무효화했습니다.", userId);

		// 6. 새로운 Access Token 및 Refresh Token 발급
		LoginResponse newTokens = generateNewTokens(userDetails);
		log.info("사용자 {}에게 새로운 액세스 토큰과 리프레시 토큰을 발급했습니다.", userId);

		return newTokens;
	}

}

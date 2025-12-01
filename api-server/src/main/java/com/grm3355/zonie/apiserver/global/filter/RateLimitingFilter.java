package com.grm3355.zonie.apiserver.global.filter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grm3355.zonie.apiserver.global.service.RateLimitingService;
import com.grm3355.zonie.commonlib.global.exception.ErrorCode;
import com.grm3355.zonie.commonlib.global.response.ApiResponse;
import com.grm3355.zonie.commonlib.global.util.JwtTokenProvider;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

	private static final PathMatcher pathMatcher = new AntPathMatcher();

	//스프링시큐리티에서 과도한 요청회수 필터링
	// 인증이 필요한 API 그룹
	// DB Write 및 고비용 인증 관련 - User ID 기반
	// 60회/60초 제한 (1 RPS)
	private static final List<String> AUTH_ENDPOINTS = Arrays.asList(
		"/api/v1/auth/tokens",
		"/api/v1/locations/verification/festivals/*",         // 위치 인증
		"/api/v1/user/me",                                   // GET 사용자 프로필 조회 (인증 필수)
		"/api/v1/user/me/quit",                              // POST 회원 탈퇴
		"/api/v1/festivals/*/chat-rooms",                    // POST 채팅방 생성
		"/api/v1/chat-rooms/my-rooms",                       // GET 내 채팅방 목록
		"/api/v1/chat-rooms/*/join",                         // POST 채팅방 입장
		"/api/v1/chat-rooms/*/leave",                        // POST 채팅방 퇴장
		"/api/v1/messages/*/like"                            // POST 메시지 좋아요
	);

	// Read API 그룹 (축제 목록, 검색)
	// 크롤링 방어 목적 - IP 기반
	// 180회/60초 제한 (3 RPS)
	private static final List<String> READ_ENDPOINTS = Arrays.asList(
		"/api/v1/festivals",
		"/api/v1/festivals/*",
		"/api/v1/festivals/regions",
		"/api/v1/festivals/count",
		"/api/v1/search",
		"/api/v1/search/festivals",
		"/api/v1/search/chat-rooms",
		"/api/v1/festivals/*/chat-rooms"// , // GET 축제별 채팅방 목록
		// "/api/v1/chat-rooms/{roomId}/messages" // 채팅방 과거 메시지 조회: 인증 필요하지만 MongoDB I/O가 Block될 수 있음.
	);

	/**
	 * 현재 시스템은 2000 VUs에서 P95 응답 시간 1.37s, 276 RPS 요청 처리함.
	 * 2~3배 이상의 트래픽 고려, 1/10 이하의 속도로 방어.
	 * 한 유저가 이 api를 초당 1회 이상 호출할 이유가 없음.
	 * 60회/60초 = 초당 1회까지 허용
	 */
	// 임계치 정의
	private static final int MAX_REQUESTS_WRITE_AUTH = 60;   // 1 RPS: DB Write API 보호 목적
	private static final int MAX_REQUESTS_READ = 180;        // 3 RPS: 크롤링 방어 목적
	private static final int WINDOW_SECONDS = 60;            // 윈도우 시간 (초)

	private final RateLimitingService rateLimitingService;
	private final ObjectMapper objectMapper;
	private final JwtTokenProvider jwtTokenProvider;

	@Override
	protected void doFilterInternal(
		@NonNull HttpServletRequest request,
		@NonNull HttpServletResponse response,
		@NonNull FilterChain filterChain
	) throws ServletException, IOException {

		String requestUri = request.getRequestURI();
		String method = request.getMethod(); // GET, POST를 구분하기 위해 method 추가
		String rateLimitKey;
		int currentMaxRequests;

		boolean isAuthTarget = isUriMatch(requestUri, AUTH_ENDPOINTS);
		boolean isReadTarget = isUriMatch(requestUri, READ_ENDPOINTS);

		if (!isAuthTarget && !isReadTarget) {
			filterChain.doFilter(request, response);
			return;
		}

		// 2. 임계치 및 Key 설정
		boolean useAuthPolicy = isAuthTarget && isHighCostRequest(method, requestUri, requestUri);

		if (useAuthPolicy) {
			// ====================================================================
			// Type A: Write/Auth API (User ID 기준 60회/60초)
			// ====================================================================
			currentMaxRequests = MAX_REQUESTS_WRITE_AUTH;
			String jwt = extractJwt(request);

			if (jwt != null) {
				try {
					// 토큰이 유효하면 User ID 기준으로 제한 (DB 접근 x)
					String userId = jwtTokenProvider.getUserIdFromToken(jwt);
					rateLimitKey = userId + ":" + requestUri;
				} catch (JwtException | IllegalArgumentException e) {
					// 토큰 만료/유효하지 않으면 IP 기준으로 대체 제한
					rateLimitKey = getClientIp(request) + ":" + requestUri;
				}
			} else {
				// 토큰이 없으면 (예: 최초 토큰 발급 시도) IP 기준으로 제한
				rateLimitKey = getClientIp(request) + ":" + requestUri;
			}
		} else if (isReadTarget) {
			// ====================================================================
			// Type B: Public Read API (IP 기준 180회/60초)
			// ====================================================================
			currentMaxRequests = MAX_REQUESTS_READ;
			// Public API는 악성 크롤링 방어를 위해 IP 기준으로 제한
			rateLimitKey = getClientIp(request) + ":" + requestUri;
		} else {
			// Rate Limit 적용 x
			filterChain.doFilter(request, response);
			return;
		}

		// 3. Rate Limit 검사 실행
		if (!rateLimitingService.allowRequest(rateLimitKey, currentMaxRequests, WINDOW_SECONDS)) {
			log.warn("Rate limit exceeded for Key: {}", rateLimitKey);
			sendTooManyRequestsResponse(response);
			return;
		}

		filterChain.doFilter(request, response);
	}

	/**
	 * AntPathMatcher: URI 패턴 매칭
	 * @param requestUri 현재 요청 URI
	 * @param patterns List<String>으로 정의된 패턴 목록
	 * @return 매칭되는 패턴이 하나라도 있으면 true
	 */
	private boolean isUriMatch(String requestUri, List<String> patterns) {
		return patterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, requestUri));
	}

	/**
	 * HighCost 요청(Write/Update/Latent Read) 여부
	 * - POST, PATCH, DELETE는 항상 High Cost로.
	 * - 특정 GET 요청도 (Latency가 높으면) High Cost로.
	 */
	private boolean isHighCostRequest(String method, String requestUri, String normalizedUri) {
		if (method.equals("POST") || method.equals("PATCH") || method.equals("DELETE")) {
			return true;
		}

		// GET 요청 중 Latency가 높음
		if (method.equals("GET")) {
			List<String> highLatencyReads = Arrays.asList(
				"/api/v1/user/me",           // 프로필 조회 (복호화/캐시 미스 리스크)
				"/api/v1/chat-rooms/my-rooms" // 내 채팅방 목록 (DB 조인 부하)
			);
			// AUHT_ENDPOINTS에 포함된 GET 요청들만 체크
			return isUriMatch(requestUri, highLatencyReads);
		}
		return false;
	}

	// Request Header -> JWT 파싱
	private String extractJwt(HttpServletRequest request) {
		String headerAuth = request.getHeader("Authorization");
		if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
			return headerAuth.substring(7);
		}
		return null;
	}

	private String getClientIp(HttpServletRequest request) {
		String xfHeader = request.getHeader("X-Forwarded-For");
		if (xfHeader == null || !xfHeader.contains(".")) {
			return request.getRemoteAddr();
		}
		return xfHeader.split(",")[0];
	}

	private void sendTooManyRequestsResponse(HttpServletResponse response) throws IOException {
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");

		ApiResponse<Object> apiResponse = ApiResponse.failure(
			ErrorCode.TOO_MANY_REQUESTS.toString(),
			"요청 횟수가 너무 많습니다. 잠시 후 다시 시도해주세요."
		);
		String jsonResponse = objectMapper.writeValueAsString(apiResponse);
		response.getWriter().write(jsonResponse);
	}
}

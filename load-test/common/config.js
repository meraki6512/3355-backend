// load-test/common/config.js
// 테스트 서버의 URL
// 로컬 주소
// export const API_BASE_URL = 'http://localhost:8080/api';
// export const CHAT_WEBSOCKET_URL = 'ws://localhost:8081/chat';
// 실제 프로덕션 환경 주소
export const API_BASE_URL = 'https://api.zony.kro.kr/api';
export const CHAT_WEBSOCKET_URL = 'wss://ws.zony.kro.kr/chat';

// -------------------------------------------------------------------------
// 사용자 인증 토큰 (JWT) 설정
// -------------------------------------------------------------------------
// 방금 curl로 발급받으신 토큰으로 아래 값을 교체해주세요.
// const ACCESS_TOKEN = '';
const ACCESS_TOKEN = 'eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIzNDY5ODRjMy01ZTFhLTRlNmMtYWU3OS05NzBmODc3NTVjNmYiLCJpYXQiOjE3NjM5OTA4NDMsImV4cCI6MTc2NDA3NzI0MywiYXV0aCI6IlVTRVIifQ.JzRTbjhi2NnTrK3g5SWQu6_T3aqatpEQ5h7NabTCPYDNefUpb8kCZ2G4BbX94h4uP-GcdXtJe8ZCeHPvMfw2lw';

// -------------------------------------------------------------------------

/**
 * 인증이 필요한 요청에 사용할 헤더를 반환합니다.
 * @returns {object} Authorization 헤더가 포함된 k6 params 객체
 */
export function getAuthHeaders() {
    if (!ACCESS_TOKEN || ACCESS_TOKEN.includes('여기에')) {
        throw new Error('config.js 파일의 ACCESS_TOKEN이 유효하지 않습니다. 실제 토큰으로 교체해주세요.');
    }
    return {
        headers: {
            'Authorization': `Bearer ${ACCESS_TOKEN}`,
            'Content-Type': 'application/json',
        },
    };
}
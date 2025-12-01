// load-test/api-server.test.js
import http from 'k6/http';
import {check, sleep} from 'k6';
import {Trend} from 'k6/metrics';
import {API_BASE_URL} from './common/config.js';

// --- 1. 전역 설정 및 메트릭 ---
const festivalListTrend = new Trend('http_req_duration_festivals_list');
const userProfileTrend = new Trend('http_req_duration_user_profile');
const createChatRoomTrend = new Trend('http_req_duration_create_chatroom');
const myChatRoomsTrend = new Trend('http_req_duration_my_chat_rooms_list');

const VUS_COUNT = 1000;
// Write 부하 분산을 위해 생성할 테스트 축제 개수
// 2500 VU * 10% (채팅방 생성 비중) = 250개의 VU가 채팅방을 생성 시도
// 각 축제당 30개 제한을 감안하여 넉넉하게 100개 축제 생성. (2500 / 30 = 약 84개 필요)
const FESTIVAL_COUNT = 100;

/**
 * 배열에서 무작위 항목을 선택하는 함수
 * @param {Array<string>} array - 항목이 담긴 배열
 * @returns {string} 배열에서 무작위로 선택된 항목
 */
function randomItem(array) {
    if (!array || array.length === 0) {
        return null;
    }
    const randomIndex = Math.floor(Math.random() * array.length);
    return array[randomIndex];
}

// --- 2. Options 설정 (2500 VUs) ---
export const options = {
    // Setup 시간: 넉넉하게 10분 (2500개 토큰 생성 시간 고려)
    setupTimeout: '10m',
    scenarios: {
        api_stress: {
            executor: 'constant-vus',
            vus: VUS_COUNT,
            duration: '2m',
            exec: 'default',
        },
    },
    thresholds: {
        'http_req_failed': ['rate<0.05'],
        'http_req_duration': ['p(95)<1500'], // 95% 요청이 1.5초 이내 처리
    },
};

// --- 3. SETUP 함수: 테스트 환경 설정 및 데이터 준비 (토큰 2500개 및 축제 100개 생성) ---
export function setup() {
    console.log(`--- SETUP: Starting test data setup for ${VUS_COUNT} VUs (Tokens & ${FESTIVAL_COUNT} Festivals) ---`);

    const setupTokens = [];
    // 생성된 모든 축제의 ID를 저장할 배열
    const setupFestivalIds = [];

    // 3-1. 마스터 토큰 발급
    const masterAuthRes = http.post(`${API_BASE_URL}/v1/test-management/auth/tokens`, JSON.stringify({
        lat: 37.5665, lon: 126.9780
    }), {headers: {'Content-Type': 'application/json'}});

    if (!check(masterAuthRes, {'SETUP: Master token created': (r) => r.status === 201})) {
        throw new Error('Failed to get master token during setup.');
    }

    const masterAccessToken = masterAuthRes.json('data.accessToken');
    const masterHeaders = {
        headers: {
            'Authorization': `Bearer ${masterAccessToken}`,
            'Content-Type': 'application/json'
        }
    };

    // 3-2. Write 부하 분산을 위한 다수의 테스트용 축제 생성 (100개)
    for (let i = 0; i < FESTIVAL_COUNT; i++) {
        if (i > 0 && i % 10 === 0) sleep(1); // 10개마다 잠시 대기

        const festivalPayload = JSON.stringify({
            title: `Stress Test Festival ${i} - ${new Date().getTime()}`,
            addr1: `Stress Test Festival Address ${i}`,
            contentId: Math.floor(Math.random() * 1000000) + i,
            eventStartDate: "2025-01-01", eventEndDate: "2025-12-31",
            lat: 37.5665, lon: 126.9780, region: "SEOUL",
            chatRoomCount: 0,
        });
        const festivalRes = http.post(`${API_BASE_URL}/v1/test-management/festivals`, festivalPayload, masterHeaders);

        if (!check(festivalRes, {[`SETUP: Festival ${i} created`]: (r) => r.status === 200})) {
            console.error(`[SETUP ERROR] Festival creation failed for ID ${i}. Status: ${festivalRes.status}`);
            // 축제 생성에 실패하면 토큰 생성 전에 Setup 실패 처리
            throw new Error('Failed to create festival during setup.');
        }

        setupFestivalIds.push(festivalRes.json('data.festivalId'));
    }

    // 3-3. 모든 VU (2500개)의 고유 토큰 발급
    for (let i = 0; i < VUS_COUNT; i++) {
        // 토큰 발급 속도 제어를 위해 100개마다 잠시 대기
        if (i % 100 === 0 && i !== 0) {
            sleep(1);
        }

        const authRes = http.post(
            `${API_BASE_URL}/v1/test-management/auth/tokens`,
            JSON.stringify({lat: 37.5665 + i * 0.00001, lon: 126.9780 + i * 0.00001}),
            {headers: {'Content-Type': 'application/json'}}
        );

        if (!check(authRes, {[`auth token created for VU ${i}`]: (r) => r.status === 201})) {
            console.error(`[SETUP ERROR] Token creation failed for VU ${i}. Status: ${authRes.status}, Body: ${authRes.body}`);
            throw new Error(`Setup failed at VU ${i}. Check logs for details.`);
        }

        setupTokens.push(`Bearer ${authRes.json('data.accessToken')}`);
    }

    console.log(`--- SETUP: Completed. Festivals: ${setupFestivalIds.length}, Tokens: ${setupTokens.length} ---`);

    // default 함수로 전달할 데이터를 객체로 묶어 반환
    return {
        tokens: setupTokens,
        // 단일 ID 대신 축제 ID 배열 전체를 전달
        festivalIds: setupFestivalIds
    };
}

// --- 4. DEFAULT 함수: 실제 부하 테스트 실행 (모든 VU에서 반복 실행) ---
export default function (data) {

    const VUS_TOKENS = data.tokens;
    // 축제 ID 배열을 받아옴
    const TEST_FESTIVAL_IDS = data.festivalIds;

    // 현재 VU의 고유 토큰과 헤더 설정
    const token = VUS_TOKENS[__VU - 1];
    const headers = {
        headers: {
            'Authorization': token,
            'Content-Type': 'application/json'
        }
    };

    // 5. 시나리오 실행 (채팅방 생성 시나리오 비중 복구)
    const scenario = randomItem([
        'userProfile', 'userProfile', 'userProfile',  // 30%
        'festivalList', 'festivalList',     // 'festivalList',
        'myChatRooms', 'myChatRooms',                 // 20%
        'totalSearch', 'totalSearch',                 // 20%
        'createChatRoom'                              // 10%
    ]);

    switch (scenario) {
        case 'festivalList':
            runFestivalListTest();
            break;
        case 'userProfile':
            runUserProfileTest(headers);
            break;
        case 'myChatRooms':
            runMyChatRoomsTest(headers);
            break;
        case 'createChatRoom':
            // **무작위 축제 ID를 선택하여 채팅방 생성**
            const randomFestivalId = randomItem(TEST_FESTIVAL_IDS);
            runCreateChatRoomTest(headers, randomFestivalId);
            break;
        case 'totalSearch':
            runTotalSearchTest();
            break;
    }

    sleep(1);
}

// --- 6. 시나리오 함수 ---

// 시나리오 1: 축제 목록 조회 (Read, DB/캐싱 부하)
function runFestivalListTest() {
    const res = http.get(`${API_BASE_URL}/v1/festivals?region=SEOUL&page=1&pageSize=10`);
    check(res, {'festival list: status is 200': (r) => r.status === 200});
    festivalListTrend.add(res.timings.duration);
}

// 시나리오 2: 사용자 프로필 조회 (Read, CPU 복호화 부하)
function runUserProfileTest(headers) {
    const res = http.get(`${API_BASE_URL}/v1/user/me`, headers);
    check(res, {'user profile: status is 200': (r) => r.status === 200});
    userProfileTrend.add(res.timings.duration);
}

// 시나리오 3: 채팅방 생성 (Write, DB 트랜잭션 부하)
function runCreateChatRoomTest(headers, festivalId) {
    const url = `${API_BASE_URL}/v1/festivals/${festivalId}/chat-rooms`;
    const payload = JSON.stringify({
        // VU가 바뀌어도 고유한 제목을 유지하도록
        title: `Stress Room ${__VU}-${new Date().getTime()}`,
        lat: 37.5665,
        lon: 126.9780,
    });

    const res = http.post(url, payload, headers);

    // 성공 시나리오 외에, 409 Conflict (제한 초과) 발생 시에도 성공으로 간주하지 않도록 201만 체크 유지
    const success = check(res, {'create chatroom: status is 201': (r) => r.status === 201});
    createChatRoomTrend.add(res.timings.duration);

    if (!success) {
        // 2500 VU 테스트에서 오류가 발생하면 로그가 폭주할 수 있으므로, 중요 오류만 기록
        if (res.status !== 201) {
            console.error(`[VU ${__VU}] Chat Room creation failed on Festival ${festivalId}. Status: ${res.status}`);
        }
    }
}

// 시나리오 4: 나의 채팅방 목록 조회 (Read - High Traffic)
function runMyChatRoomsTest(headers) {
    const res = http.get(`${API_BASE_URL}/v1/chat-rooms/my-rooms`, headers);
    check(res, {'my chat-rooms list: status is 200': (r) => r.status === 200});
    myChatRoomsTrend.add(res.timings.duration);
}

// 시나리오 5: 통합 검색 (Read, TotalSearchController.getTotalSearch)
function runTotalSearchTest() {
    // 통합 검색 API 호출: 키워드와 페이징 파라미터 사용
    const res = http.get(`${API_BASE_URL}/v1/search?keyword=Test`);
    const success = check(res, {'total search: status is 200': (r) => r.status === 200});

    if (!success) {
        console.log(`[VU ${__VU}] Total Search Failed - Status: ${res.status}, Body: ${res.body}`);
        // check(res, {'total search: status is 400': (r) => r.status === 400});
    }
}

// // 시나리오 5: 축제 키워드 검색 (Read, TotalSearchController.getFestivalSearch)
// function runFestivalSearchTest() {
//     const res = http.get(`${API_BASE_URL}/v1/search/festivals?keyword=축제&page=1&pageSize=10`);
//     const success = check(res, {'festival search: status is 200': (r) => r.status === 200});
//     if (!success) {
//         check(res, {'festival search: status is 400': (r) => r.status === 400});
//         console.log(`[VU ${__VU}] Festival Search Failed - Status: ${res.status}, Body: ${res.body}`);
//     }
// }
//
// // 시나리오 6: 채팅방 키워드 검색 (Read, TotalSearchController.getChatroomSearch)
// function runChatRoomSearchTest() {
//     const res = http.get(`${API_BASE_URL}/v1/search/chat-rooms?keyword=채팅&page=1&pageSize=10`);
//     const success = check(res, {'chatroom search: status is 200': (r) => r.status === 200});
//     if (!success) {
//         check(res, {'chatroom search: status is 400': (r) => r.status === 400});
//         console.log(`[VU ${__VU}] Chatroom Search Failed - Status: ${res.status}, Body: ${res.body}`);
//     }
// }
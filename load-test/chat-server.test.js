// load-test/chat-server.test.js
import http from 'k6/http';
import ws from 'k6/ws';
import {check, sleep} from 'k6';
import {Trend} from 'k6/metrics';
import {API_BASE_URL, CHAT_WEBSOCKET_URL} from './common/config.js';

const messageRoundtripTrend = new Trend('websocket_message_roundtrip_duration');
const CHAT_VUS_COUNT = 100; // (동시 웹소켓 연결 부하)

export const options = {
    scenarios: {
        chat: {
            executor: 'constant-vus',
            vus: CHAT_VUS_COUNT,
            duration: '2m', // 2분간 부하 유지
            exec: 'runChatTest',
        },
    },
    thresholds: {
        'websocket_message_roundtrip_duration': ['p(95)<1200'], // 95% 메시지 왕복 시간이 1.2초 미만
        'checks': ['rate>0.99'], // 웹소켓 에러율 1% 미만
    }
};

export function setup() {
    console.log('--- Test Setup Started ---');

    const vusCount = CHAT_VUS_COUNT;
    const accessTokens = [];

    // --- 1단계: 마스터 토큰 발급 (축제/채팅방 생성용) ---
    const masterAuthRes = http.post(
        `${API_BASE_URL}/v1/test-management/auth/tokens`,
        JSON.stringify({lat: 37.5665, lon: 126.9780}),
        {headers: {'Content-Type': 'application/json'}}
    );
    check(masterAuthRes, {'master token created': (r) => r.status === 201});
    const masterAccessToken = masterAuthRes.json('data.accessToken');
    if (!masterAccessToken) {
        throw new Error('Setup failed: Could not retrieve master token.');
    }

    const setupHeaders = {
        headers: {
            'Authorization': `Bearer ${masterAccessToken}`,
            'Content-Type': 'application/json'
        }
    };
    console.log('1. Master Token created successfully.');

    // --- 2단계: 테스트용 축제 생성 ---
    const festivalPayload = JSON.stringify({
        title: `Chat Stress Test Festival - ${new Date().getTime()}`,
        addr1: "Chat Stress Test Festival Address",
        contentId: Math.floor(Math.random() * 100000) + 2000,
        eventStartDate: "2025-01-01", eventEndDate: "2025-12-31",
        lat: 37.5665, lon: 126.9780, region: "SEOUL",
        chatRoomCount: 0
    });
    const festivalRes = http.post(`${API_BASE_URL}/v1/test-management/festivals`, festivalPayload, setupHeaders);
    check(festivalRes, {'test festival created': (r) => r.status === 200});
    if (festivalRes.status !== 200) {
        console.error(`[Setup] Festival Creation FAILED! Status: ${festivalRes.status}, Body: ${festivalRes.body}`);
    }

    const festivalId = festivalRes.json('data.festivalId');
    if (!festivalId) {
        throw new Error('Setup failed: Could not create festival.');
    }
    console.log(`2. Test Festival created successfully. festivalId: ${festivalId}`);

    // --- 3단계: 테스트용 채팅방 생성 ---
    // POST /api/v1/festivals/{festivalId}/chat-rooms
    const chatRoomPayload = JSON.stringify({
        title: "자동 생성된 테스트 채팅방",
        lat: 37.5665, lon: 126.9780, maxParticipants: vusCount + 10 // VU 수보다 크게 설정하여 정원 초과 방지
    });
    const chatRoomRes = http.post(`${API_BASE_URL}/v1/festivals/${festivalId}/chat-rooms`, chatRoomPayload, setupHeaders);
    check(chatRoomRes, {'test chatroom created': (r) => r.status === 201});
    const chatRoomId = chatRoomRes.json('data.chatRoomId');
    if (!chatRoomId) {
        throw new Error('Setup failed: Could not create chat room.');
    }
    console.log(`3. Test Chat Room created successfully. chatRoomId: ${chatRoomId}`);

    // --- 4단계: 모든 VU의 고유 토큰 발급 ---
    // POST /api/v1/test-management/auth/tokens
    for (let i = 0; i < vusCount; i++) {
        const authRes = http.post(
            `${API_BASE_URL}/v1/test-management/auth/tokens`,
            JSON.stringify({lat: 37.5665 + i * 0.00001, lon: 126.9780 + i * 0.00001}), // 고유 위치 부여
            {headers: {'Content-Type': 'application/json'}}
        );
        check(authRes, {[`auth token created for VU ${i}`]: (r) => r.status === 201});
        const accessToken = authRes.json('data.accessToken');
        if (!accessToken) {
            throw new Error(`Setup failed: Could not retrieve access token for VU ${i}.`);
        }
        accessTokens.push(accessToken);
    }
    console.log(`4. ${vusCount} Auth Tokens created successfully.`);

    console.log('--- Test Setup Finished ---');

    return {accessTokens: accessTokens, festivalId: festivalId, chatRoomId: chatRoomId};
}


export function runChatTest(data) {
    // 현재 VU에 할당된 고유 토큰
    const accessToken = data.accessTokens[__VU - 1];

    // --- 1. 위치 인증 API 호출 (HTTP) ---
    // POST /api/v1/locations/verification/festivals/{festivalId}
    const locationAuthUrl = `${API_BASE_URL}/v1/locations/verification/festivals/${data.festivalId}`;
    const locationPayload = JSON.stringify({lat: 37.5665, lon: 126.9780});
    const authHeaders = {headers: {'Authorization': `Bearer ${accessToken}`, 'Content-Type': 'application/json'}};

    const apiRes = http.post(locationAuthUrl, locationPayload, authHeaders);
    const locationAuthSuccess = check(apiRes, {'location auth success': (r) => r.status === 200});
    if (!locationAuthSuccess) {
        return;
    } // 위치 인증 실패 시 테스트 중단
    sleep(1);

    // --- 2. 웹소켓 연결 및 채팅 테스트 (WebSocket) ---
    const url = CHAT_WEBSOCKET_URL;

    const res = ws.connect(url, null, function (socket) {
        let lastSentTime = 0;

        socket.on('open', () => {
            // STOMP CONNECT 프레임에 고유 토큰 사용 (인증)
            const connectFrame = 'CONNECT\nAuthorization:Bearer ' + accessToken + '\naccept-version:1.2\n\n\0';
            socket.send(connectFrame);
        });

        socket.on('message', (wsData) => {
            if (wsData.startsWith('MESSAGE')) {
                const parts = wsData.split('\n\n');
                const body = parts.length > 1 ? parts[1].replace('\0', '') : '';
                if (lastSentTime > 0 && body) {
                    try {
                        const receivedMessage = JSON.parse(body);
                        // 자신이 보낸 메시지(VU 고유 ID 포함)를 수신했을 때 왕복 시간을 측정
                        if (receivedMessage.content && receivedMessage.content.includes(`VU ${__VU}`)) {
                            const receivedTime = new Date().getTime();
                            const roundtripTime = receivedTime - lastSentTime;
                            messageRoundtripTrend.add(roundtripTime);
                            lastSentTime = 0; // 측정 완료 후 초기화
                        }
                    } catch (e) { /* ignore non-JSON messages */
                    }
                }
            } else if (wsData.startsWith('CONNECTED')) {
                // 연결 성공 시 구독
                const subscribeFrame = `SUBSCRIBE\nid:sub-1\ndestination:/sub/chat-rooms/${data.chatRoomId}\n\n\0`;
                socket.send(subscribeFrame);

                // 3초마다 메시지 발행 (초당 300 TPS 가정 시, 100 VU는 3초에 한 번 발행)
                socket.setInterval(() => {
                    const messageContent = `hello from VU ${__VU} at ${new Date().toISOString()}`;
                    const payload = JSON.stringify({content: messageContent});
                    // SEND 프레임 발행
                    const sendFrame = `SEND\ndestination:/app/chat-rooms/${data.chatRoomId}/send\ncontent-type:application/json\n\n${payload}\0`;
                    socket.send(sendFrame);
                    lastSentTime = new Date().getTime(); // 송신 시간 기록
                }, 3000);
            }
        });

        socket.on('close', () => {
        });
        socket.on('error', (e) => {
            console.error(`[VU ${__VU}] WS Error: `, e.error());
        });

        socket.setTimeout(() => {
            socket.close();
        }, 118000); // 2분 (120초) duration에 맞춰 설정
    });

    check(res, {'WebSocket handshake success': (r) => r && r.status === 101});
}
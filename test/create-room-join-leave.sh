#!/bin/bash
set -euo pipefail # 에러 발생 시 즉시 중단

echo "--- 1단계: 환경 설정 및 유저 토큰 발급 ---"
RESPONSE=$(curl -X POST 'http://localhost:8080/api/v1/test-management/auth/tokens' \
  --header 'Content-Type: application/json' \
  --data '{"lat": 37.5665, "lon": 126.9780}')
ACCESS_TOKEN=$(echo "$RESPONSE" | jq -r '.data.accessToken')

if [ "$ACCESS_TOKEN" = "null" ] || [ -z "$ACCESS_TOKEN" ]; then
    echo "Access Token 추출 실패. 응답을 확인하세요."
    echo "$RESPONSE"
    exit 1
fi

USER_ID=$(echo "$ACCESS_TOKEN" | awk -F'.' '{print $2}' | base64 --decode 2>/dev/null | jq -r '.sub' || echo "UNKNOWN_USER")
echo "Guest User ID: $USER_ID"
echo "Access Token (순수값): $ACCESS_TOKEN"

echo "--- 2단계: 테스트 축제 생성 및 ID 획득 (안전한 JSON 구성) ---"
UNIQUE_SUFFIX=$(date +%s)
CONTENT_ID="${UNIQUE_SUFFIX}"
TITLE="테스트 퇴장 축제 ${UNIQUE_SUFFIX}"

echo "새 CONTENT_ID: $CONTENT_ID"

PAYLOAD_JSON=$(cat <<EOF
{
  "title": "$TITLE",
  "addr1": "서울 테스트 주소",
  "contentId": $CONTENT_ID,
  "eventStartDate": "2025-01-01",
  "eventEndDate": "2025-12-31",
  "lat": 37.5665,
  "lon": 126.9780,
  "region": "SEOUL"
}
EOF
)

# 생성된 JSON이 유효한지 확인 (디버깅)
echo "🔍 JSON Payload: $PAYLOAD_JSON"

# 유효한 JSON을 사용하여 curl 요청
RESPONSE=$(curl -X POST 'http://localhost:8080/api/v1/test-management/festivals' \
  --header 'Content-Type: application/json' \
  --data "$PAYLOAD_JSON")

FESTIVAL_ID=$(echo "$RESPONSE" | jq -r '.data.festivalId')

if [ "$FESTIVAL_ID" = "null" ] || [ -z "$FESTIVAL_ID" ]; then
    echo "Festival ID 추출 실패. 응답을 확인하세요."
    echo "$RESPONSE"
    exit 1
fi
echo "Festival ID (순수값): $FESTIVAL_ID"

echo "--- 3단계: 채팅방 생성 및 ID 획득 ---"
RESPONSE=$(curl -X POST "http://localhost:8080/api/v1/festivals/$FESTIVAL_ID/chat-rooms" \
  --header 'Content-Type: application/json' \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  --data '{"title": "방장 퇴장 테스트 룸", "lat": 37.5665, "lon": 126.9780}')
ROOM_ID=$(echo "$RESPONSE" | jq -r '.data.chatRoomId')

if [ "$ROOM_ID" = "null" ] || [ -z "$ROOM_ID" ]; then
    echo "ChatRoom ID 추출 실패. 응답을 확인하세요."
    echo "$RESPONSE"
    exit 1
fi
echo "ChatRoom ID: $ROOM_ID"

echo "--- 4단계: '나의 채팅방 목록' 확인 (생성 직후) ---"
echo "(확인 사항): 응답에 해당 방이 포함되어야 합니다."
curl -s -X GET 'http://localhost:8080/api/v1/chat-rooms/my-rooms?page=1&pageSize=10&order=DATE_DESC' \
  --header "Authorization: Bearer $ACCESS_TOKEN" | jq '.data.content[] | select(.chatRoomId == "'"$ROOM_ID"'")'

echo "--- 5단계: 채팅방 퇴장 및 DB 레코드 삭제 확인 (핵심) ---"
echo "(확인 사항): HTTP 204 No Content가 반환되어야 합니다."
curl -X POST "http://localhost:8080/api/v1/chat-rooms/$ROOM_ID/leave" \
  --header "Authorization: Bearer $ACCESS_TOKEN" -I | head -n 1
echo "--- 5.3. DB에서 ChatRoomUser 레코드 확인 (직접 PostgreSQL 실행) ---"
echo "SELECT * FROM chat_room_user WHERE chat_room_id = '$ROOM_ID' AND user_id = '$USER_ID';"
echo "(확인 사항): 위 쿼리 결과가 0행(Zero Rows)이어야 합니다."

echo "--- 6단계: '나의 채팅방 목록' 재확인 (퇴장 후) ---"
echo "(확인 사항): 목록 조회 결과가 비어있거나, 해당 방이 포함되지 않아야 합니다."
curl -s -X GET 'http://localhost:8080/api/v1/chat-rooms/my-rooms?page=1&pageSize=10&order=DATE_DESC' \
  --header "Authorization: Bearer $ACCESS_TOKEN" | jq '.data.content[] | select(.chatRoomId == "'"$ROOM_ID"'")'
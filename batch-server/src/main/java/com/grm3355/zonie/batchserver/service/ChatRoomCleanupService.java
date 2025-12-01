package com.grm3355.zonie.batchserver.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.grm3355.zonie.commonlib.domain.message.entity.Message;
import com.grm3355.zonie.commonlib.global.util.RedisScanService;
import com.mongodb.client.result.UpdateResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomCleanupService {

	// Redis Key Prefix
	private static final String LAST_MSG_AT_KEY_PREFIX = "chatroom:last_msg_at:";
	private static final String LAST_MSG_CONTENT_KEY_PREFIX = "chatroom:last_msg_content:";
	private static final String NICKNAME_SEQ_KEY_PREFIX = "chatroom:nickname_seq:";
	private final RedisScanService redisScanService;
	private final MongoTemplate mongoTemplate; // MongoDB 연결을 위해 주입

	/**
	 * PG DB에서 삭제된 ChatRoom ID에 연관된 모든 Redis 캐시와 MongoDB 메시지를 정리합니다.
	 * @param chatRoomIds PG DB에서 삭제된 채팅방 ID 목록
	 */
	public void cleanupDeletedRoomData(Collection<String> chatRoomIds) {
		if (chatRoomIds.isEmpty())
			return;

		// 1. Redis Key 정리
		Set<String> keysToDelete = new HashSet<>();
		chatRoomIds.forEach(roomId -> {
			// 채팅방 관련 키 추가
			keysToDelete.add(LAST_MSG_AT_KEY_PREFIX + roomId);
			keysToDelete.add(LAST_MSG_CONTENT_KEY_PREFIX + roomId);
			keysToDelete.add(NICKNAME_SEQ_KEY_PREFIX + roomId);
		});
		log.info("keysToDelete.size: {}", keysToDelete.size());

		// Redis 키 일괄 삭제
		if (!keysToDelete.isEmpty()) {
			redisScanService.deleteKeys(keysToDelete);
			log.info("[Redis] ChatRoom ID 기반 {}개의 캐시 키 정리 완료.", keysToDelete.size());
		}

		// 2. MongoDB 메시지 삭제
		long deletedMessages = deleteMessagesByRoomIds(chatRoomIds);

		// 3. MongoDB 메시지 삭제 후, 좋아요 관련 Redis 키 정리
		//    -> MessageLikeCleanupJob은 30일이 지난 키를 정리하지만, DB 삭제 시 즉시 정리하는 것이 메모리 효율적입니다.
		cleanupMessageLikeKeys(chatRoomIds);

		log.info("[Cleanup Summary] 채팅방 {}개 삭제 관련 MongoDB {}건, Redis {}개 정리 완료.",
			chatRoomIds.size(), deletedMessages, keysToDelete.size());
	}

	/**
	 * MongoDB에서 메시지 삭제 및 좋아요 키 정리 (메시지 ID를 모르므로 Room ID를 기준으로 처리)
	 */
	private long deleteMessagesByRoomIds(Collection<String> chatRoomIds) {
		// 메시지 삭제

		// Hard Delete
		// Query query = Query.query(Criteria.where("chatRoomId").in(chatRoomIds));
		// DeleteResult result = mongoTemplate.remove(query, Message.class);
		// log.info("[MongoDB] 채팅방 {}개에 해당하는 메시지 {}건 삭제 완료.", chatRoomIds.size(), result.getDeletedCount());
		// return result.getDeletedCount();
		// if (chatRoomIds.isEmpty()) {
		// 	return 0;
		// }

		// 1. 쿼리: 삭제 대상 chatRoomId 목록에 포함되고, 아직 deletedAt이 null인 메시지
		Query query = Query.query(
			Criteria.where("chatRoomId").in(chatRoomIds)
				.and("deletedAt").is(null)
		);

		// 2. 업데이트: deletedAt 필드를 현재 시각으로 설정
		Update update = new Update().set("deletedAt", LocalDateTime.now());

		// 3. 실행
		// mongoTemplate.updateMulti: 여러 도큐먼트를 한 번에 업데이트
		UpdateResult result = mongoTemplate.updateMulti(query, update, Message.class);
		log.info("[MongoDB] 채팅방 {}개에 해당하는 메시지 {}건 Soft Delete 완료.",
			chatRoomIds.size(), result.getModifiedCount());

		return result.getModifiedCount();
	}

	/**
	 * MongoDB 메시지가 삭제된 Room ID에 해당하는 좋아요 Redis 키를 정리합니다.
	 * 이 메서드를 cleanupDeletedRoomData 내에서 deleteMessagesByRoomIds 호출 후 호출해야 함
	 */
	private void cleanupMessageLikeKeys(Collection<String> chatRoomIds) {
		if (chatRoomIds.isEmpty())
			return;

		// 1. MongoDB에서 삭제된 ChatRoom ID에 해당하는 Message ID 목록 조회
		Query idQuery = Query.query(Criteria.where("chatRoomId").in(chatRoomIds));
		idQuery.fields().include("_id"); // _id (Message ID)만 조회하도록 최적화

		// Message 객체 대신, String (Message ID) 리스트로 조회
		List<String> messageIds = mongoTemplate.find(idQuery, String.class, "messages");

		if (messageIds.isEmpty()) {
			log.info("[Redis 좋아요 정리] 삭제할 메시지 ID가 없습니다.");
			return;
		}

		// 2. Redis 삭제할 키 목록 생성
		Set<String> keysToDelete = messageIds.stream()
			.flatMap(id -> Stream.of(
				"message:like_count:" + id,
				"message:liked_by:" + id
			))
			.collect(Collectors.toSet());

		// 3. Redis 키 일괄 삭제
		if (!keysToDelete.isEmpty()) {
			redisScanService.deleteKeys(keysToDelete);
			log.info("[Redis 좋아요 정리] 메시지 {}개 분량의 좋아요 키 {}개 정리 완료.", messageIds.size(), keysToDelete.size());
		}
	}
}

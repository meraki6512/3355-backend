package com.grm3355.zonie.batchserver.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.grm3355.zonie.batchserver.job.ChatRoomDeletionJob;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomDeletionScheduler {

	private final ChatRoomDeletionJob chatRoomDeletionJob;

	/**
	 * [Job 1]: 참여자가 0명인 채팅방 삭제 (빈 방 정리)
	 * 스케줄: 1분마다 실행
	 */
	@Scheduled(fixedRate = 60000) // 1분 = 60000ms
	public void runCleanupEmptyRooms() {
		try {
			log.info(">>>> Scheduler: CleanupEmptyRoomsJob (1분 주기) 시작");
			chatRoomDeletionJob.cleanupEmptyRooms();
			log.info(">>>> Scheduler: CleanupEmptyRoomsJob 완료");
		} catch (Exception e) {
			log.error("CleanupEmptyRoomsJob 실행 중 오류 발생", e);
		}
	}

	/**
	 * [Job 2]: 마지막 대화가 24시간 지난 채팅방 삭제 (비활성 방 정리)
	 * 스케줄: 6시간마다 실행 (0시, 6시, 12시, 18시)
	 */
	@Scheduled(cron = "0 0 0/6 * * ?")
	public void runCleanupInactiveRooms() {
		try {
			log.info(">>>> Scheduler: CleanupInactiveRoomsJob (6시간 주기) 시작");
			chatRoomDeletionJob.cleanupInactiveRooms();
			log.info(">>>> Scheduler: CleanupInactiveRoomsJob 완료");
		} catch (Exception e) {
			log.error("CleanupInactiveRoomsJob 실행 중 오류 발생", e);
		}
	}
}

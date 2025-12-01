package com.grm3355.zonie.apiserver.domain.chatroom.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grm3355.zonie.apiserver.domain.location.service.LocationService;
import com.grm3355.zonie.commonlib.domain.festival.entity.Festival;
import com.grm3355.zonie.commonlib.domain.festival.repository.FestivalRepository;
import com.grm3355.zonie.commonlib.global.exception.BusinessException;
import com.grm3355.zonie.commonlib.global.exception.ErrorCode;

@Service
public class FestivalInfoService {
	private final FestivalRepository festivalRepository;

	public FestivalInfoService(FestivalRepository festivalRepository, LocationService locationService) {
		this.festivalRepository = festivalRepository;
	}

	//축제테이블에 존재여부체크
	@Transactional(readOnly = true)
	public Festival getDataValid(long festivalId, int dayNum) {
		return festivalRepository
			.findByIsValidFestival(festivalId, dayNum)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "축제 관련정보가 없습니다."));
	}

	//채팅방 등록시 chatRoomCount++
	@Transactional
	public void increaseChatRoomCount(Long festivalId) {
		festivalRepository.updateFestivalChatRoomCount(festivalId);
	}

	/**
	 * 사용자가 축제 반경 내에 있는지 PostGIS를 이용해 검증합니다.
	 * @param festivalId 축제 ID
	 * @param lat 사용자 위도
	 * @param lon 사용자 경도
	 * @param maxRadius 최대 허용 반경 (km)
	 * @return 반경 내에 있으면 true
	 */
	@Deprecated
	@Transactional(readOnly = true)
	public boolean isUserWithinFestivalRadius(long festivalId, double lat, double lon, double maxRadius) {
		// PostGIS 쿼리(findDistanceToFestival)를 호출하여 거리를 km 단위로 가져옵니다.
		double distanceKm = festivalRepository.findDistanceToFestival(festivalId, lon, lat)
			.orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "축제 위치 정보를 찾을 수 없습니다."));
		return distanceKm <= maxRadius;
	}
}

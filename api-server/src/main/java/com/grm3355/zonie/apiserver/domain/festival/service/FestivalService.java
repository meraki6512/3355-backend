package com.grm3355.zonie.apiserver.domain.festival.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grm3355.zonie.apiserver.domain.festival.dto.FestivalCreateRequest;
import com.grm3355.zonie.apiserver.domain.festival.dto.FestivalDetailResponse;
import com.grm3355.zonie.apiserver.domain.festival.dto.FestivalResponse;
import com.grm3355.zonie.apiserver.domain.festival.dto.FestivalSearchRequest;
import com.grm3355.zonie.apiserver.domain.festival.dto.RegionResponse;
import com.grm3355.zonie.apiserver.domain.festival.enums.FestivalOrderType;
import com.grm3355.zonie.apiserver.domain.festival.enums.FestivalStatus;
import com.grm3355.zonie.commonlib.domain.festival.entity.Festival;
import com.grm3355.zonie.commonlib.domain.festival.entity.FestivalDetailImage;
import com.grm3355.zonie.commonlib.domain.festival.repository.FestivalDetailImageRepository;
import com.grm3355.zonie.commonlib.domain.festival.repository.FestivalRepository;
import com.grm3355.zonie.commonlib.global.enums.Region;
import com.grm3355.zonie.commonlib.global.exception.BusinessException;
import com.grm3355.zonie.commonlib.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class FestivalService {

	private final FestivalDetailImageRepository detailImageRepository;
	private final FestivalRepository festivalRepository;
	@Value("${chat.pre-view-day}")
	private int preview_days; //시작하기전 몇일전부터 보여주기

	/**
	 * 축제목록
	 * 정렬:
	 * 1: 상태 정렬 (진행중 vs 예정(Start > Today) vs 종료) - 종료한 상태(End < Today)는 없을 테지만 가장 마지막에 배치
	 * 2: 날짜 정렬 - 진행중 기본: 시작일 빠른 순, 예정 기본: 시작일 가까운 순(임박순)
	 * 3: 제목 정렬 (동일 날짜면 제목 가나다순)
	 */
	@Transactional(readOnly = true)
	public Page<FestivalResponse> getFestivalList(FestivalSearchRequest req) {

		//축제 위치기반 체크
		if (req.isPs()) {
			if (req.getLat() == null || req.getLon() == null || req.getRadius() == null) {
				throw new BusinessException(ErrorCode.BAD_REQUEST, "위도, 경도, 반경을 정확하게 입력하시기 바랍니다.");
			}
		}

		Sort sort;
		Sort statusGroupSort = getStatusGroupSort();

		// 기본 정렬 (req.getOrder() == null)은 DATE_DESC로 간주
		if (req.getOrder() == FestivalOrderType.DATE_DESC || req.getOrder() == null) {
			// 기본 정렬: 상태 정렬 ASC (Ongoing -> Upcoming -> Ended), 날짜 내림차순, 제목 오름차순
			Sort dateSort = Sort.by(Sort.Direction.DESC, "event_start_date");
			Sort titleSort = Sort.by(Sort.Direction.ASC, "title");
			sort = statusGroupSort.and(dateSort).and(titleSort);

		} else if (req.getOrder() == FestivalOrderType.DATE_ASC) {
			// 시작일 오름차순: 상태 정렬 ASC, 날짜 오름차순, 제목 오름차순
			Sort dateSort = Sort.by(Sort.Direction.ASC, "event_start_date");
			Sort titleSort = Sort.by(Sort.Direction.ASC, "title");
			sort = statusGroupSort.and(dateSort).and(titleSort);

		} else if (req.getOrder() == FestivalOrderType.TITLE_ASC) {
			// 제목 가나다순: 상태 정렬 ASC, 제목 오름차순, 날짜 오름차순 (동일 제목일 경우 날짜로)
			Sort dateSort = Sort.by(Sort.Direction.ASC, "event_start_date");
			Sort titleSort = Sort.by(Sort.Direction.ASC, "title");
			sort = statusGroupSort.and(titleSort).and(dateSort);

		} else if (req.getOrder() == FestivalOrderType.TITLE_DESC) {
			// 제목 역순: 상태 정렬 ASC, 제목 내림차순, 날짜 오름차순 (동일 제목일 경우 날짜로)
			Sort dateSort = Sort.by(Sort.Direction.ASC, "event_start_date");
			Sort titleSort = Sort.by(Sort.Direction.DESC, "title");
			sort = statusGroupSort.and(titleSort).and(dateSort);

		} else {
			// 그 외의 경우 (발생하면 안 되지만, 안정성을 위해 기본값 적용)
			Sort dateSort = Sort.by(Sort.Direction.DESC, "event_start_date");
			Sort titleSort = Sort.by(Sort.Direction.ASC, "title");
			sort = statusGroupSort.and(dateSort).and(titleSort);
		}

		Pageable pageable = PageRequest.of(req.getPage() - 1, req.getPageSize(), sort);

		// ListType 내용 가져오기
		Page<Festival> pageList = getFestivalListType(req, pageable);

		// 페이지 변환
		List<FestivalResponse> dtoPage = pageList.stream().map(FestivalResponse::fromEntity)
			.collect(Collectors.toList());

		return new PageImpl<>(dtoPage, pageable, pageList.getTotalElements());
	}

	/**
	 * 상태 정렬은 모든 정렬의 최우선 순위가 되어야 함
	 */
	private Sort getStatusGroupSort() {
		// 상태 그룹 정렬 (Ongoing: 0, Upcoming: 1, Ended: 2)
		// Native Query에 사용되는 컬럼명(event_start_date, event_end_date)을 직접 사용
		// f. 별칭을 사용하여 PostGIS Native 쿼리 환경에서 커스텀 정렬이 무시되지 않도록 함
		return JpaSort.unsafe(Sort.Direction.ASC,
			"""
				(CASE
					WHEN f.event_start_date <= CURRENT_DATE AND f.event_end_date >= CURRENT_DATE THEN 0
					WHEN f.event_start_date > CURRENT_DATE THEN 1
					ELSE 2
				END)
				""");
	}

	// 축제별 채팅방 검색조건별 목록 가져오기
	public Page<Festival> getFestivalListType(FestivalSearchRequest req, Pageable pageable) {

		Region region = req.getRegion();
		String regionStr = region != null ? region.toString() : null;

		FestivalStatus status = req.getStatus();
		String statusStr = status != null ? status.toString() : null;

		// 위치기반 검색이면
		if (req.isPs()) {
			return festivalRepository
				.getFestivalLocationBased(req.getLat(), req.getLon(), req.getRadius() * 1000.0, preview_days, pageable);
		} else {    // 전체검색이면
			return festivalRepository
				.getFestivalList(regionStr, statusStr, req.getKeyword(), preview_days, pageable);
		}
	}

	/**
	 * 축제 상세내용
	 * @param festivalId 축제 아이디
	 * @return 페스티벌 Response
	 */
	@Transactional
	public FestivalDetailResponse getFestivalContent(long festivalId) {
		Festival festival = festivalRepository
			.findByIsValidFestival(festivalId, preview_days)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "관련 내용을 찾을 수 없습니다."));

		log.info("festival.content_id :{}", festival.getContentId());

		List<FestivalDetailImage> images =
			detailImageRepository.findByFestival_ContentId(festival.getContentId());

		return FestivalDetailResponse.fromEntity(festival, images);
	}

	/**
	 * 지역분류 가져오기
	 * @return list
	 */
	public List<Map<String, String>> getRegionList() {
		return Arrays.stream(Region.values()).map(region -> Map.of(
			"region", region.getName(),
			"code", region.name()
		)).toList();
	}

	public List<RegionResponse> getRegionCounts() {

		// DB에서: { "서울", 23 }, { "경기/인천", 15 }
		List<Object[]> rows = festivalRepository.countByRegionGroup(preview_days);

		// String → count 매핑
		Map<String, Long> dbCounts = new HashMap<>();
		for (Object[] row : rows) {
			dbCounts.put((String)row[0], (Long)row[1]);
		}

		List<RegionResponse> result = new ArrayList<>();

		// Enum 순서대로 출력 (SEOUL → GYEONGGI → …)
		for (Region region : Region.values()) {
			String enumCode = region.name();      // SEOUL
			String regionName = region.getName(); // "서울"
			Long count = dbCounts.getOrDefault(enumCode, 0L);
			result.add(new RegionResponse(enumCode, regionName, count));
		}

		return result;
	}

	/**
	 * 지역별 축제 개수 조회
	 * (getFestivalListType의 필터 조건 중 'preview_days'를 동일하게 적용)
	 *
	 * @param region Region Enum
	 * @return 해당 지역의 축제 개수
	 */
	@Transactional(readOnly = true)
	public long getFestivalCountByRegion(Region region) {
		if (region == null) {
			throw new BusinessException(ErrorCode.BAD_REQUEST,
				"지역 코드를 정확하게 입력하세요. 지역코드 정보는 다음과 같습니다.\n SEOUL(\"서울\"),\n"
					+ "\tGYEONGGI(\"경기/인천\"),\n"
					+ "\tCHUNGCHEONG(\"충청/대전/세종\"),\n"
					+ "\tGANGWON(\"강원\"),\n"
					+ "\tGYEONGBUK(\"경북/대구/울산\"),\n"
					+ "\tGYEONGNAM(\"경남/부산\"),\n"
					+ "\tJEOLLA(\"전라/광주\"),\n"
					+ "\tJEJU(\"제주\")}");
		}

		// 3. Repository에 count용 메서드 호출: getFestivalList와 동일하게 preview_days를 적용하여 노출될 축제만 카운트
		return festivalRepository.countFestivalsByRegion(
			region.toString(),
			preview_days
		);
	}

	/**
	 * 축제 생성 (테스트용)
	 * @param req 생성 요청 DTO
	 * @return 생성된 페스티벌 Response
	 */
	@Transactional
	public FestivalResponse createFestival(FestivalCreateRequest req) {
		Point position;
		try {
			// PostGIS Point 객체 생성: Point(경도 위도) 형식
			String wkt = String.format("POINT(%s %s)", req.getLon(), req.getLat());

			// WKTReader는 org.locationtech.jts.io.WKTReader를 사용합니다.
			// PostgreSQL/PostGIS의 SRID 4326을 수동으로 설정합니다.
			WKTReader reader = new WKTReader();
			position = (Point)reader.read(wkt);
			position.setSRID(4326); // SRID 4326 (WGS 84) 설정

		} catch (ParseException e) {
			log.error("Position parsing error: {}", e.getMessage());
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "좌표 데이터 처리 오류");
		}

		int contentId = req.getContentId();
		String addr1 = req.getAddr1();
		String title = req.getTitle();
		String region = req.getRegion();
		String firstImage = req.getFirstImage();
		LocalDate eventStartDate = req.getEventStartDate();
		LocalDate eventEndDate = req.getEventEndDate();
		String mapx = String.valueOf(req.getLon());
		String mapy = String.valueOf(req.getLat());

		// status: 진행 중으로 설정
		String status = FestivalStatus.ONGOING.toString();

		// areaCode: 임의의 숫자
		Integer areaCode = ThreadLocalRandom.current().nextInt(1, 30);

		// Festival 엔티티 생성
		Festival festival = Festival.builder()
			.title(title)
			.addr1(addr1)
			.contentId(contentId)
			.eventStartDate(eventStartDate)
			.eventEndDate(eventEndDate)
			.firstImage(firstImage)
			.region(region)
			.position(position)
			.mapx(mapx)
			.mapy(mapy)
			.status(status)
			.areaCode(areaCode)
			.build();

		Festival savedFestival = festivalRepository.save(festival);
		log.info("새로운 축제 생성됨: FestivalId={}, Title={}", savedFestival.getFestivalId(), savedFestival.getTitle());

		return FestivalResponse.fromEntity(savedFestival);
	}

	public void syncTotalParticipantCounts() {
		festivalRepository.syncTotalParticipantCounts();
	}
}

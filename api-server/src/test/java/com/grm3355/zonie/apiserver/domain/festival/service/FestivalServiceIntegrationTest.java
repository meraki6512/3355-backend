package com.grm3355.zonie.apiserver.domain.festival.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import com.grm3355.zonie.apiserver.BaseIntegrationTest;
import com.grm3355.zonie.apiserver.domain.festival.dto.FestivalResponse;
import com.grm3355.zonie.apiserver.domain.festival.dto.FestivalSearchRequest;
import com.grm3355.zonie.apiserver.domain.festival.enums.FestivalOrderType;
import com.grm3355.zonie.commonlib.domain.festival.entity.Festival;
import com.grm3355.zonie.commonlib.domain.festival.repository.FestivalRepository;

@DisplayName("축제 목록 정렬 통합 테스트")
@SpringBootTest(
	properties = {
		"spring.data.mongodb.auto-configuration.enabled=false",
		"spring.data.redis.repositories.enabled=false",
		"spring.cache.type=none"
	}
)
class FestivalServiceIntegrationTest extends BaseIntegrationTest {

	private static final double TEST_LAT = 37.5;
	private static final double TEST_LON = 127.0;
	@Autowired
	private FestivalService festivalService;
	@Autowired
	private FestivalRepository festivalRepository;

	/**
	 * 테스트용 축제 생성 헬퍼 (DB에 직접 삽입)
	 */
	private void insertFestival(String title, LocalDate startDate, LocalDate endDate) {
		// PostGIS Point 객체 생성에 필요한 GeometryFactory 및 WKTReader 임시 정의
		GeometryFactory geometryFactory = new GeometryFactory();
		Point position = geometryFactory.createPoint(
			new Coordinate(TEST_LON, TEST_LAT)
		);
		position.setSRID(4326); // SRID 4326 설정 (PostGIS 규격)

		Festival festival = Festival.builder()
			.title(title)
			.addr1("주소")
			.contentId((int)(Math.random() * 100000))
			.eventStartDate(startDate)
			.eventEndDate(endDate)
			.region("SEOUL")
			.mapx(String.valueOf(TEST_LON))
			.mapy(String.valueOf(TEST_LAT))
			.position(position) // [수정] Position 필드 설정
			.build();
		festivalRepository.save(festival);
	}

	@Test
	@DisplayName("DATE_ASC 정렬: 상태 우선순위 (Ongoing:0 -> Upcoming:1 -> Ended:2) 검증")
	@Transactional
	void testDateAscSortingByStatusPriority() {
		// Given
		LocalDate today = LocalDate.now();

		// 1. Ended: 어제 종료된 축제
		insertFestival("Z_Ended_Fest", today.minusDays(5), today.minusDays(1));

		// 2. Ongoing: 오늘까지 진행되는 축제 (5일전 ~ 오늘)
		insertFestival("A_Ongoing_Fest", today.minusDays(5), today);

		// 3. Upcoming: 내일 시작하는 축제 (내일 ~)
		insertFestival("B_Upcoming_Fest", today.plusDays(1), today.plusDays(5));

		// 4. Ongoing: 오래된 Ongoing 축제 (1년전 ~ 2일전)
		insertFestival("C_Old_Ongoing", today.minusYears(1), today.plusDays(2));

		// 5. Upcoming: 먼 미래 축제
		insertFestival("D_Far_Upcoming", today.plusYears(1), today.plusYears(1).plusDays(5));

		FestivalSearchRequest request = FestivalSearchRequest.builder()
			.page(1).pageSize(10)
			.status(com.grm3355.zonie.apiserver.domain.festival.enums.FestivalStatus.ALL)
			.order(FestivalOrderType.DATE_ASC)
			.build();

		// When
		Page<FestivalResponse> resultPage = festivalService.getFestivalList(request);
		List<String> sortedTitles = resultPage.getContent().stream()
			.map(FestivalResponse::getTitle)
			.toList();

		// Then:
		// =============== 실제 정렬 결과 ===============
		System.out.println("--- Actual Sorted Titles ---");
		for (int i = 0; i < sortedTitles.size(); i++) {
			System.out.println("Index " + i + ": " + sortedTitles.get(i));
		}
		System.out.println("----------------------------");
		// ==========================================

		// 7일 기간 필터 적용 시 3개 축제만 남아야 함 (T-1년, T-10일 시작 축제 제외)
		assertEquals(3, sortedTitles.size(), "총 3개의 축제가 조회되어야 합니다.");

		assertTrue(sortedTitles.get(0).startsWith("C_Old_Ongoing"), "첫 번째는 Ongoing 그룹에서 가장 빠른 시작일 축제여야 합니다.");
		assertTrue(sortedTitles.get(1).startsWith("A_Ongoing_Fest"), "두 번째는 Ongoing 그룹에서 다음으로 빠른 시작일 축제여야 합니다.");
		assertTrue(sortedTitles.get(2).startsWith("B_Upcoming_Fest"), "세 번째는 Upcoming 그룹에서 가장 임박한 축제여야 합니다.");
	}

	@Test
	@DisplayName("DATE_ASC 정렬: 동일 상태/날짜 내 제목(Title) 오름차순 검증")
	@Transactional
	void testDateAscSortingByTitleTieBreaker() {
		// Given: 동일 상태, 동일 시작일의 축제 3개 생성
		LocalDate tomorrow = LocalDate.now().plusDays(1);
		LocalDate dayAfter = LocalDate.now().plusDays(5);

		// 1. B: 시작일 내일
		insertFestival("B_Upcoming", tomorrow, dayAfter);

		// 2. A: 시작일 내일
		insertFestival("A_Upcoming", tomorrow, dayAfter);

		// 3. C: 시작일 내일
		insertFestival("C_Upcoming", tomorrow, dayAfter);

		FestivalSearchRequest request = FestivalSearchRequest.builder()
			.page(1).pageSize(10)
			.order(FestivalOrderType.DATE_ASC)
			.build();

		// When
		Page<FestivalResponse> resultPage = festivalService.getFestivalList(request);
		List<String> sortedTitles = resultPage.getContent().stream()
			.map(FestivalResponse::getTitle)
			.toList();

		// Then: 제목 가나다 순으로 정렬되어야 함
		assertEquals(3, sortedTitles.size());
		assertEquals("A_Upcoming", sortedTitles.get(0));
		assertEquals("B_Upcoming", sortedTitles.get(1));
		assertEquals("C_Upcoming", sortedTitles.get(2));
	}
}

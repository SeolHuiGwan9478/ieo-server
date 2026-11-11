package com.ieo.server.domain.restarea.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.ieo.server.domain.restarea.entity.RestArea;
import com.ieo.server.domain.restarea.repository.RestAreaRepository;
import com.ieo.server.domain.restarea.service.ExpresswayRestAreaSource.ExpresswayRestArea;
import com.ieo.server.domain.restarea.service.RestAreaLocationSource.RestAreaLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** 휴게소 주간 갱신. 두 API는 가짜 목록으로 바꾼다 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RestAreaSyncServiceTest {

	@Autowired
	private RestAreaSyncService syncService;

	@Autowired
	private RestAreaRepository repository;

	@MockitoBean
	private ExpresswayRestAreaSource expressway;

	@MockitoBean
	private RestAreaLocationSource locations;

	private static ExpresswayRestArea okcheon(String facilities) {
		return new ExpresswayRestArea("A00032", "옥산(부산)휴게소", "경부선", "부산", List.of(facilities.split("\\|")), true,
				"043-260-1053", "충북 청원군 옥산면오산리 589-15");
	}

	private static ExpresswayRestArea plain(int i) {
		return new ExpresswayRestArea("B%05d".formatted(i), "보통" + i + "(서울)휴게소", "경부선", "서울", List.of("수유실"), false, null, null);
	}

	private static final RestAreaLocation OKCHEON = new RestAreaLocation("옥산(부산)", "하행", 36.657843, 127.370066);

	private RestArea saved(String code) {
		return repository.findAll().stream().filter(area -> area.getCode().equals(code)).findFirst().orElseThrow();
	}

	@Test
	void 처음에는_모두_넣고_표준데이터_이름으로_좌표와_상하행을_붙인다() {
		given(expressway.fetchAll()).willReturn(List.of(okcheon("수유실|수면실|샤워실|세탁실"), plain(1)));
		given(locations.fetchAll()).willReturn(List.of(OKCHEON));

		RestAreaSyncService.SyncResult result = syncService.sync();

		assertThat(result.added()).isEqualTo(2);
		assertThat(result.withoutLocation()).isEqualTo(1);
		RestArea area = saved("A00032");
		assertThat(area.latitude()).isEqualTo(36.657843);
		assertThat(area.getRoadDirection()).isEqualTo("하행");
		assertThat(area.hasTruckLounge()).isTrue();
		assertThat(saved("B00001").getLocation()).isNull();
	}

	@Test
	void 같은_내용이면_그대로_두고_바뀐_것만_고친다() {
		given(expressway.fetchAll()).willReturn(List.of(okcheon("수유실|수면실|샤워실|세탁실"), plain(1)));
		given(locations.fetchAll()).willReturn(List.of(OKCHEON));
		syncService.sync();
		String hash = saved("B00001").getContentHash();

		given(expressway.fetchAll()).willReturn(List.of(okcheon("수유실|샤워실"), plain(1)));
		RestAreaSyncService.SyncResult result = syncService.sync();

		assertThat(result.updated()).isEqualTo(1);
		assertThat(result.unchanged()).isEqualTo(1);
		assertThat(saved("A00032").hasTruckLounge()).isFalse();
		assertThat(saved("B00001").getContentHash()).isEqualTo(hash);
	}

	@Test
	void 사라진_휴게소는_지우지_않고_표시만_하고_다시_보이면_푼다() {
		List<ExpresswayRestArea> all = new ArrayList<>(IntStream.rangeClosed(1, 20).mapToObj(RestAreaSyncServiceTest::plain).toList());
		given(expressway.fetchAll()).willReturn(all);
		given(locations.fetchAll()).willReturn(List.of());
		syncService.sync();

		given(expressway.fetchAll()).willReturn(all.subList(0, 19));
		RestAreaSyncService.SyncResult result = syncService.sync();

		assertThat(result.missing()).isEqualTo(1);
		assertThat(repository.count()).isEqualTo(20);
		assertThat(saved("B00020").getMissingSince()).isNotNull();

		given(expressway.fetchAll()).willReturn(all);
		syncService.sync();
		assertThat(saved("B00020").getMissingSince()).isNull();
	}

	@Test
	void 받은_수가_너무_적으면_아무것도_바꾸지_않는다() {
		List<ExpresswayRestArea> all = IntStream.rangeClosed(1, 20).mapToObj(RestAreaSyncServiceTest::plain).toList();
		given(expressway.fetchAll()).willReturn(all);
		given(locations.fetchAll()).willReturn(List.of());
		syncService.sync();

		given(expressway.fetchAll()).willReturn(all.subList(0, 10));
		RestAreaSyncService.SyncResult result = syncService.sync();

		assertThat(result.skipped()).isTrue();
		assertThat(repository.findAllByMissingSinceIsNull()).hasSize(20);

		given(expressway.fetchAll()).willReturn(List.of());
		assertThat(syncService.sync().skipped()).isTrue();
	}

	@Test
	void 표준데이터를_못_받으면_저장된_좌표를_지키고_직접_넣은_좌표는_덮어쓰지_않는다() {
		given(expressway.fetchAll()).willReturn(List.of(okcheon("수유실|수면실|샤워실|세탁실")));
		given(locations.fetchAll()).willReturn(List.of(OKCHEON));
		syncService.sync();

		given(locations.fetchAll()).willReturn(List.of());
		syncService.sync();
		assertThat(saved("A00032").latitude()).isEqualTo(36.657843);

		saved("A00032").fixLocation(36.6, 127.3);
		given(locations.fetchAll()).willReturn(List.of(OKCHEON));
		syncService.sync();
		assertThat(saved("A00032").latitude()).isEqualTo(36.6);
		assertThat(saved("A00032").isLocationManual()).isTrue();
	}
}

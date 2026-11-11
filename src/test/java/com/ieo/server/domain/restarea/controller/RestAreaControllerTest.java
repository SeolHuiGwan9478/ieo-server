package com.ieo.server.domain.restarea.controller;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.restarea.entity.RestArea;
import com.ieo.server.domain.restarea.repository.RestAreaRepository;
import com.ieo.server.domain.restarea.service.RestAreaSnapshot;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.global.auth.JwtProvider;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** 경부선 실제 좌표(표준데이터 2026-09-17)로 근처 휴게소 찾기 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RestAreaControllerTest {

	/** 청주 남쪽 경부고속도로 위 (옥산과 죽암 사이) */
	private static final String LAT = "36.60";
	private static final String LNG = "127.40";
	private static final List<String> LOUNGE = List.of("수유실", "수면실", "샤워실", "세탁실");

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RestAreaRepository repository;

	@Autowired
	private JwtProvider jwtProvider;

	private MockMvc mockMvc;
	private String bearer;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		User user = userRepository.save(User.create("01090000801", UserRole.DRIVER));
		bearer = "Bearer " + jwtProvider.issue(user.getId()).accessToken();
		save("A1", "죽암(부산)휴게소", "부산", "하행", LOUNGE, 36.4868101, 127.429261);
		save("A4", "옥산(부산)휴게소", "부산", "하행", LOUNGE, 36.657743, 127.369817);
		save("A5", "청주(서울)휴게소", "서울", "상행", LOUNGE, 36.716005, 127.349315);
		save("A6", "천안삼거리(서울)휴게소", "서울", "상행", List.of("수유실", "쉼터"), 36.787809, 127.173455);
		save("A7", "망향(부산)휴게소", "부산", "하행", LOUNGE, 36.8556501, 127.180929);
	}

	private void save(String code, String name, String direction, String roadDirection, List<String> facilities, double lat, double lng) {
		repository.save(RestArea.create(new RestAreaSnapshot(code, name, "경부선", direction, roadDirection, facilities,
				true, "043-000-0000", null, lat, lng), Instant.parse("2026-11-11T00:00:00Z")));
	}

	@Test
	void 근처_라운지를_가까운_순으로_방향_뺀_이름과_출처와_함께() throws Exception {
		mockMvc.perform(get("/api/rest-areas").header("Authorization", bearer).param("lat", LAT).param("lng", LNG))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[*].name", contains("옥산휴게소", "죽암휴게소", "청주휴게소", "망향휴게소")))
				.andExpect(jsonPath("$.items[0].direction").value("부산"))
				.andExpect(jsonPath("$.items[0].roadDirection").value("하행"))
				.andExpect(jsonPath("$.items[0].lounge").value(true))
				.andExpect(jsonPath("$.items[0].distanceMeters").isNumber())
				.andExpect(jsonPath("$.attribution").isNotEmpty());
	}

	@Test
	void 반경을_넓히고_all이면_라운지_없는_화물_휴게소도() throws Exception {
		mockMvc.perform(get("/api/rest-areas").header("Authorization", bearer)
						.param("lat", LAT).param("lng", LNG).param("radiusKm", "100").param("all", "true"))
				.andExpect(jsonPath("$.items[*].name", contains("옥산휴게소", "죽암휴게소", "청주휴게소", "천안삼거리휴게소", "망향휴게소")))
				.andExpect(jsonPath("$.items[3].lounge").value(false));
	}

	@Test
	void 사라진_휴게소는_보이지_않고_잘못된_값은_400() throws Exception {
		repository.findAll().stream().filter(area -> area.getCode().equals("A4"))
				.forEach(area -> area.markMissing(Instant.parse("2026-11-12T00:00:00Z")));
		mockMvc.perform(get("/api/rest-areas").header("Authorization", bearer)
						.param("lat", LAT).param("lng", LNG).param("limit", "1"))
				.andExpect(jsonPath("$.items[0].name").value("죽암휴게소"));
		mockMvc.perform(get("/api/rest-areas").header("Authorization", bearer)
						.param("lat", LAT).param("lng", LNG).param("radiusKm", "500"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/rest-areas").header("Authorization", bearer)
						.param("lat", LAT).param("lng", LNG).param("limit", "50"))
				.andExpect(status().isBadRequest());
	}
}

package com.ieo.server.domain.site.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.site.entity.SiteAttribute;
import com.ieo.server.domain.site.entity.SiteNote;
import com.ieo.server.domain.site.enums.SiteAttributeKey;
import com.ieo.server.domain.site.repository.SiteAttributeRepository;
import com.ieo.server.domain.site.repository.SiteNoteRepository;
import com.ieo.server.domain.site.repository.SiteRepository;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import com.ieo.server.domain.vehicle.repository.VehicleRepository;
import com.ieo.server.global.auth.JwtProvider;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SiteControllerTest {

	/** 칠곡 물류센터 */
	private static final double LAT = 35.9550;
	private static final double LNG = 128.5280;

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private SiteRepository siteRepository;

	@Autowired
	private SiteAttributeRepository attributeRepository;

	@Autowired
	private SiteNoteRepository noteRepository;

	@Autowired
	private JwtProvider jwtProvider;

	private MockMvc mockMvc;
	private Driver driver;
	private String bearer;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		User user = userRepository.save(User.create("01090000601", UserRole.DRIVER));
		driver = driverRepository.save(Driver.of(user));
		bearer = "Bearer " + jwtProvider.issue(user.getId()).accessToken();
	}

	@Test
	void 새_상하차지를_등록하면_201과_기본_반경_200m() throws Exception {
		register("칠곡 물류센터", LAT, LNG)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("칠곡 물류센터"))
				.andExpect(jsonPath("$.geofenceRadiusM").value(200))
				.andExpect(jsonPath("$.existing").value(false));
	}

	@Test
	void 백_미터_안에_있으면_새로_만들지_않고_기존_상하차지를_돌려준다() throws Exception {
		Site existing = siteRepository.save(Site.create("칠곡 물류센터", "경북 칠곡군", LAT, LNG, driver));

		// 위도 0.0005도 ≈ 56m
		register("칠곡물류 정문", LAT + 0.0005, LNG)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(existing.getId()))
				.andExpect(jsonPath("$.name").value("칠곡 물류센터"))
				.andExpect(jsonPath("$.existing").value(true));

		// 위도 0.0015도 ≈ 167m
		register("칠곡 2센터", LAT + 0.0015, LNG)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.existing").value(false));
	}

	@Test
	void 좌표가_범위를_벗어나거나_빠지면_400() throws Exception {
		register("어딘가", 95, LNG)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SITE_400_LOCATION"));
		mockMvc.perform(post("/api/sites").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"a\",\"address\":\"b\",\"lat\":35.9}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_400"));
	}

	@Test
	void 근처_상하차지를_가까운_순으로_반경_안만_보여준다() throws Exception {
		siteRepository.save(Site.create("2km 떨어진 곳", "b", LAT + 0.018, LNG, driver));
		siteRepository.save(Site.create("500m 떨어진 곳", "a", LAT + 0.0045, LNG, driver));
		siteRepository.save(Site.create("5km 떨어진 곳", "c", LAT + 0.045, LNG, driver));

		mockMvc.perform(get("/api/sites/nearby").header("Authorization", bearer)
						.param("lat", String.valueOf(LAT)).param("lng", String.valueOf(LNG)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].name").value("500m 떨어진 곳"))
				.andExpect(jsonPath("$[0].distanceM").value(500))
				.andExpect(jsonPath("$[1].name").value("2km 떨어진 곳"));

		mockMvc.perform(get("/api/sites/nearby").header("Authorization", bearer)
						.param("lat", String.valueOf(LAT)).param("lng", String.valueOf(LNG)).param("radiusM", "10000"))
				.andExpect(jsonPath("$.length()").value(3));
	}

	@Test
	void 근처_찾기_반경이_범위를_벗어나거나_좌표가_없으면_400() throws Exception {
		mockMvc.perform(get("/api/sites/nearby").header("Authorization", bearer)
						.param("lat", "35.9").param("lng", "128.5").param("radiusM", "20000"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/sites/nearby").header("Authorization", bearer).param("lat", "35.9"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_400"));
	}

	@Test
	void 정보_카드에_최신_항목과_메모와_높이_경고를_준다() throws Exception {
		Vehicle vehicle = Vehicle.create(driver, "경기82사1234", Tonnage.TON_5, BodyType.WING, new BigDecimal("3.60"));
		vehicle.markDefault();
		vehicleRepository.save(vehicle);
		Site site = siteRepository.save(Site.create("칠곡 물류센터", "경북 칠곡군", LAT, LNG, driver));
		attributeRepository.save(SiteAttribute.create(site, SiteAttributeKey.HEIGHT_LIMIT, "3.8", driver));
		attributeRepository.save(SiteAttribute.create(site, SiteAttributeKey.HEIGHT_LIMIT, "3.5", driver));
		attributeRepository.save(SiteAttribute.create(site, SiteAttributeKey.FORKLIFT, "true", driver));
		noteRepository.save(SiteNote.create(site, driver, "지게차 기사 점심시간은 12시부터 1시"));

		mockMvc.perform(get("/api/sites/" + site.getId()).header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("칠곡 물류센터"))
				.andExpect(jsonPath("$.attributes.heightLimitM").value(3.5))
				.andExpect(jsonPath("$.attributes.forklift").value(true))
				.andExpect(jsonPath("$.attributes.parking").doesNotExist())
				.andExpect(jsonPath("$.heightWarning.level").value("BLOCKED"))
				.andExpect(jsonPath("$.heightWarning.vehicleHeightM").value(3.6))
				.andExpect(jsonPath("$.notes.length()").value(1))
				.andExpect(jsonPath("$.notes[0].text").value("지게차 기사 점심시간은 12시부터 1시"))
				.andExpect(jsonPath("$.notes[0].mine").value(true))
				.andExpect(jsonPath("$.notes[0].driverId").doesNotExist());
	}

	@Test
	void 정보가_없는_상하차지는_빈_카드와_UNKNOWN() throws Exception {
		Site site = siteRepository.save(Site.create("성서 공단 3동", "대구 달서구", 35.84, 128.49, null));

		mockMvc.perform(get("/api/sites/" + site.getId()).header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.attributes.heightLimitM").doesNotExist())
				.andExpect(jsonPath("$.heightWarning.level").value("UNKNOWN"))
				.andExpect(jsonPath("$.notes.length()").value(0));
	}

	@Test
	void 없는_상하차지는_404_로그인_안_하면_401() throws Exception {
		mockMvc.perform(get("/api/sites/999999").header("Authorization", bearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SITE_404"));
		mockMvc.perform(get("/api/sites/999999"))
				.andExpect(status().isUnauthorized());
	}

	private ResultActions register(String name, double lat, double lng) throws Exception {
		return mockMvc.perform(post("/api/sites").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"%s\",\"address\":\"경북 칠곡군\",\"lat\":%s,\"lng\":%s}".formatted(name, lat, lng)));
	}
}

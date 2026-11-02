package com.ieo.server.domain.site.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.site.entity.SiteAttribute;
import com.ieo.server.domain.site.entity.SiteNote;
import com.ieo.server.domain.site.enums.SiteAttributeKey;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SiteRepositoryTest {

	@Autowired
	private SiteRepository siteRepository;

	@Autowired
	private SiteAttributeRepository attributeRepository;

	@Autowired
	private SiteNoteRepository noteRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DriverRepository driverRepository;

	private Driver driver;
	private Site chilgok;

	@BeforeEach
	void setUp() {
		User user = userRepository.save(User.create("01090000501", UserRole.DRIVER));
		driver = driverRepository.save(Driver.of(user));
		chilgok = siteRepository.save(Site.create("칠곡 물류센터", "경북 칠곡군", 35.9550, 128.5280, driver));
	}

	@Test
	void 위도_경도_범위_안의_상하차지만_찾는다() {
		siteRepository.save(Site.create("대구 성서 공단", "대구 달서구", 35.8400, 128.4900, driver));

		assertThat(siteRepository.findInBox(35.95, 35.96, 128.52, 128.53))
				.extracting(Site::getName)
				.containsExactly("칠곡 물류센터");
	}

	@Test
	void 항목별로_가장_최근_값만_가져온다() {
		attributeRepository.save(SiteAttribute.create(chilgok, SiteAttributeKey.HEIGHT_LIMIT, "3.5", driver));
		attributeRepository.save(SiteAttribute.create(chilgok, SiteAttributeKey.FORKLIFT, "true", driver));
		attributeRepository.save(SiteAttribute.create(chilgok, SiteAttributeKey.HEIGHT_LIMIT, "3.3", driver));

		assertThat(attributeRepository.findLatestBySiteId(chilgok.getId()))
				.extracting(SiteAttribute::getKey, SiteAttribute::getValue)
				.containsExactlyInAnyOrder(
						tuple(SiteAttributeKey.HEIGHT_LIMIT, "3.30"),
						tuple(SiteAttributeKey.FORKLIFT, "true"));
	}

	@Test
	void 정보_카드_메모는_신고가_적은_것부터_최대_5개() {
		SiteNote reported = SiteNote.create(chilgok, driver, "신고된 메모");
		reported.report();
		noteRepository.save(reported);
		for (int i = 1; i <= 5; i++) {
			noteRepository.save(SiteNote.create(chilgok, driver, "메모 " + i));
		}

		assertThat(noteRepository.findForCard(chilgok.getId(), Pageable.ofSize(5)))
				.hasSize(5)
				.extracting(SiteNote::getText)
				.doesNotContain("신고된 메모");
	}

	@Test
	void 탈퇴한_차주와의_연결을_끊어도_정보와_메모는_남는다() {
		attributeRepository.save(SiteAttribute.create(chilgok, SiteAttributeKey.FORKLIFT, "true", driver));
		noteRepository.save(SiteNote.create(chilgok, driver, "후문으로 들어가세요"));
		Long userId = driver.getUserId();

		assertThat(siteRepository.detachCreator(userId)).isEqualTo(1);
		assertThat(attributeRepository.detachReporter(userId)).isEqualTo(1);
		assertThat(noteRepository.detachAuthor(userId)).isEqualTo(1);

		assertThat(siteRepository.findById(chilgok.getId()).orElseThrow().getCreatedBy()).isNull();
		assertThat(attributeRepository.findLatestBySiteId(chilgok.getId()))
				.singleElement().extracting(SiteAttribute::getReportedBy).isNull();
		assertThat(noteRepository.findForCard(chilgok.getId(), Pageable.ofSize(5)))
				.singleElement().extracting(SiteNote::getText).isEqualTo("후문으로 들어가세요");
	}
}

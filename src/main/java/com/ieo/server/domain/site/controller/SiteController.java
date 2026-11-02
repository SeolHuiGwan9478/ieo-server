package com.ieo.server.domain.site.controller;

import com.ieo.server.domain.site.dto.CreateSiteRequest;
import com.ieo.server.domain.site.dto.NearbySiteResponse;
import com.ieo.server.domain.site.dto.SiteCardResponse;
import com.ieo.server.domain.site.dto.SiteResponse;
import com.ieo.server.domain.site.service.SiteService;
import com.ieo.server.global.auth.AuthInterceptor;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 상하차지 (SITE-001, 002, 006, 007) */
@RestController
@RequestMapping("/api/sites")
@RequiredArgsConstructor
public class SiteController {

	private final SiteService siteService;

	/** 등록. 새로 만들면 201, 100m 안의 기존 상하차지를 돌려주면 200 */
	@PostMapping
	public ResponseEntity<SiteResponse> register(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody CreateSiteRequest request) {
		SiteService.Registration registration = siteService.register(userId, request);
		HttpStatus status = registration.created() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status).body(registration.site());
	}

	/** 근처 상하차지. 반경 기본 3km, 100m~10km */
	@GetMapping("/nearby")
	public List<NearbySiteResponse> getNearby(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@RequestParam double lat,
			@RequestParam double lng,
			@RequestParam(required = false) Integer radiusM) {
		return siteService.getNearby(userId, lat, lng, radiusM);
	}

	/** 정보 카드 */
	@GetMapping("/{siteId}")
	public SiteCardResponse getCard(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long siteId) {
		return siteService.getCard(userId, siteId);
	}
}

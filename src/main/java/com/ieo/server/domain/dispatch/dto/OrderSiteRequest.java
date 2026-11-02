package com.ieo.server.domain.dispatch.dto;

import com.ieo.server.domain.site.dto.CreateSiteRequest;
import jakarta.validation.Valid;

/** 이미 있는 상하차지면 siteId, 주소 검색으로 고른 곳이면 site (100m 안에 같은 곳이 있으면 그것을 쓴다) */
public record OrderSiteRequest(Long siteId, @Valid CreateSiteRequest site) {
}

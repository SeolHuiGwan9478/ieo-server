package com.ieo.server.domain.site.dto;

import com.ieo.server.domain.site.enums.SiteAttributeKey;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 높이 제한은 "3.5"처럼 미터, 지게차·주차는 "true" / "false". 형식은 SiteAttribute가 확인한다 */
public record CreateSiteAttributeRequest(
		@NotNull SiteAttributeKey key,
		@NotBlank @Size(max = 20) String value) {
}

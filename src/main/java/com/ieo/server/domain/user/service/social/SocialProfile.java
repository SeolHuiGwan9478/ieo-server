package com.ieo.server.domain.user.service.social;

import com.ieo.server.domain.user.enums.SocialProvider;

/** 공급자가 확인해 준 사용자. providerUserId가 그 공급자 안에서의 고유 번호다. */
public record SocialProfile(SocialProvider provider, String providerUserId, String email) {
}

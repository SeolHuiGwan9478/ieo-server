package com.ieo.server.domain.document.business;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 서류 심사가 다른 기능을 막는 규칙 (application.yml의 ieo.review).
 *
 * @param gateBrokerOrders 회사 서류(사업자등록증·운송주선사업 허가증)가 모두 승인된 주선사만 오더를 올릴 수 있다 (07 인계서 13장).
 *                         테스트 프로필은 끈다. 오더를 만드는 테스트마다 서류를 승인해 두지 않아도 되게
 */
@ConfigurationProperties("ieo.review")
public record ReviewProperties(@DefaultValue("true") boolean gateBrokerOrders) {
}

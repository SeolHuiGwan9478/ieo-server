package com.ieo.server.domain.dispatch.controller;

import com.ieo.server.domain.dispatch.dto.OrderTemplateRequest;
import com.ieo.server.domain.dispatch.dto.OrderTemplateResponse;
import com.ieo.server.domain.dispatch.service.BrokerOrderService;
import com.ieo.server.global.auth.AuthInterceptor;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 주선사 콘솔: 자주 쓰는 노선 (DSP-004). 회사 담당자끼리 같이 쓴다 */
@RestController
@RequestMapping("/api/broker/order-templates")
@RequiredArgsConstructor
public class BrokerOrderTemplateController {

	private final BrokerOrderService brokerOrderService;

	@GetMapping
	public List<OrderTemplateResponse> list(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return brokerOrderService.getTemplates(userId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public OrderTemplateResponse save(@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@Valid @RequestBody OrderTemplateRequest request) {
		return brokerOrderService.saveTemplate(userId, request);
	}

	@DeleteMapping("/{templateId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long templateId) {
		brokerOrderService.deleteTemplate(userId, templateId);
	}
}

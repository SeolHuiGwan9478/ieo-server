package com.ieo.server.domain.wait.controller;

import com.ieo.server.domain.wait.dto.WaitProofResponse;
import com.ieo.server.domain.wait.service.WaitService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 주선사가 받은 링크로 보는 대기 증빙. 로그인 없이 열린다 (WAIT-020) */
@RestController
@RequiredArgsConstructor
public class WaitProofController {

	private final WaitService waitService;

	@GetMapping("/api/public/wait-proofs/{shareToken}")
	public WaitProofResponse getProof(@PathVariable String shareToken) {
		return waitService.getProof(shareToken);
	}
}

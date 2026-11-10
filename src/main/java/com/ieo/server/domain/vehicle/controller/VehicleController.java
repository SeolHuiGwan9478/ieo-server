package com.ieo.server.domain.vehicle.controller;

import com.ieo.server.domain.vehicle.dto.CreateVehicleRequest;
import com.ieo.server.domain.vehicle.dto.FuelEconomyRequest;
import com.ieo.server.domain.vehicle.dto.ManualFuelEconomyRequest;
import com.ieo.server.domain.vehicle.dto.PleosResolveResponse;
import com.ieo.server.domain.vehicle.dto.PleosVinRequest;
import com.ieo.server.domain.vehicle.dto.UpdateVehicleRequest;
import com.ieo.server.domain.vehicle.dto.VehicleResponse;
import com.ieo.server.domain.vehicle.service.VehicleService;
import com.ieo.server.global.auth.AuthInterceptor;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 내 차량 (VEH-001). 차주 계정만 쓸 수 있다. */
@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

	private final VehicleService vehicleService;

	/** 내 차량 목록. 먼저 등록한 순서 */
	@GetMapping
	public List<VehicleResponse> getMyVehicles(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return vehicleService.getMyVehicles(userId);
	}

	/** 등록. 첫 차량은 기본 차량이 된다 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public VehicleResponse register(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody CreateVehicleRequest request) {
		return vehicleService.register(userId, request);
	}

	/** 톤수·차종·높이 수정 */
	@PatchMapping("/{vehicleId}")
	public VehicleResponse update(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long vehicleId,
			@Valid @RequestBody UpdateVehicleRequest request) {
		return vehicleService.update(userId, vehicleId, request);
	}

	/** 차량 앱: 로그인한 뒤 이 트럭이 내 차량 중 어느 것인지 찾는다 (VEH-004) */
	@PostMapping("/pleos/resolve")
	public PleosResolveResponse resolvePleos(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody PleosVinRequest request) {
		return vehicleService.resolvePleos(userId, request);
	}

	/** 차량 앱: 고른 차량에 이 트럭을 연결한다 */
	@PostMapping("/{vehicleId}/pleos")
	public VehicleResponse linkPleos(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long vehicleId,
			@Valid @RequestBody PleosVinRequest request) {
		return vehicleService.linkPleos(userId, vehicleId, request);
	}

	/** 휴대폰 앱: 평소 연비(km/L)를 넣는다. null이면 비운다 (VEH-003) */
	@PutMapping("/{vehicleId}/manual-fuel-economy")
	public VehicleResponse changeManualFuelEconomy(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long vehicleId,
			@RequestBody ManualFuelEconomyRequest request) {
		return vehicleService.changeManualFuelEconomy(userId, vehicleId, request);
	}

	/** 차량 앱: Pleos 누적 연비(L/100km)를 올린다 (VEH-003) */
	@PutMapping("/{vehicleId}/fuel-economy")
	public VehicleResponse updateFuelEconomy(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long vehicleId,
			@Valid @RequestBody FuelEconomyRequest request) {
		return vehicleService.updateFuelEconomy(userId, vehicleId, request);
	}
}

package com.ieo.server.domain.vehicle.service;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.domain.vehicle.dto.CreateVehicleRequest;
import com.ieo.server.domain.vehicle.dto.FuelEconomyRequest;
import com.ieo.server.domain.vehicle.dto.PleosResolveResponse;
import com.ieo.server.domain.vehicle.dto.PleosResolveStatus;
import com.ieo.server.domain.vehicle.dto.PleosVinRequest;
import com.ieo.server.domain.vehicle.dto.UpdateVehicleRequest;
import com.ieo.server.domain.vehicle.dto.VehicleResponse;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.repository.VehicleRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 차량 등록·관리 (VEH-001). 차주 계정만 쓸 수 있다.
 * 기본 차량은 차주당 하나: 첫 차량이 기본이 되고, 기본 차량을 지우면 남은 것 중 먼저 등록한 차량이 기본이 된다.
 * Pleos 트럭(VIN)과 연결된 차량은 그 트럭에서 로그인할 때마다 기본 차량이 된다 (VEH-004).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class VehicleService {

	private final VehicleRepository vehicleRepository;
	private final DriverRepository driverRepository;
	private final VinHasher vinHasher;
	private final Clock clock;

	@Transactional(readOnly = true)
	public List<VehicleResponse> getMyVehicles(Long userId) {
		requireDriver(userId);
		return vehicleRepository.findAllByDriverUserIdOrderByCreatedAtAsc(userId).stream()
				.map(VehicleResponse::from)
				.toList();
	}

	public VehicleResponse register(Long userId, CreateVehicleRequest request) {
		Driver driver = requireDriver(userId);
		Vehicle vehicle = Vehicle.create(driver, request.plateNumber(), request.tonnage(), request.bodyType(), request.heightM());
		if (vehicleRepository.existsByPlateNumber(vehicle.getPlateNumber())) {
			throw new BusinessException(ErrorCode.VEHICLE_DUPLICATE_PLATE);
		}
		if (vehicleRepository.countByDriverUserId(userId) == 0) {
			vehicle.markDefault();
		}
		try {
			vehicleRepository.saveAndFlush(vehicle);
		} catch (DataIntegrityViolationException e) {
			// 같은 번호를 동시에 등록한 경우. plate_number 유니크 제약이 하나만 남긴다
			throw new BusinessException(ErrorCode.VEHICLE_DUPLICATE_PLATE);
		}
		if (request.vin() != null && !request.vin().isBlank()) {
			attachVin(userId, vehicle, vinHasher.hash(request.vin()));
		}
		return VehicleResponse.from(vehicle);
	}

	/**
	 * 차량에서 로그인한 뒤, 이 트럭(VIN)이 내 차량 중 어느 것인지 찾는다 (VEH-004).
	 * 차량이 한 대뿐이면 묻지 않고 연결한다. 찾거나 연결한 차량은 기본 차량이 된다.
	 */
	public PleosResolveResponse resolvePleos(Long userId, PleosVinRequest request) {
		requireDriver(userId);
		String vinHash = vinHasher.hash(request.vin());

		Optional<Vehicle> linked = vehicleRepository.findByVinHash(vinHash);
		if (linked.isPresent()) {
			if (!linked.get().isOwnedBy(userId)) {
				VehicleResponse myDefault = vehicleRepository.findByDriverUserIdAndDefaultVehicleTrue(userId)
						.map(VehicleResponse::from)
						.orElse(null);
				return new PleosResolveResponse(PleosResolveStatus.OTHER_OWNER, false, myDefault, List.of());
			}
			makeDefault(userId, linked.get());
			return new PleosResolveResponse(PleosResolveStatus.LINKED, false, VehicleResponse.from(linked.get()), List.of());
		}

		List<Vehicle> mine = vehicleRepository.findAllByDriverUserIdOrderByCreatedAtAsc(userId);
		if (mine.isEmpty()) {
			return new PleosResolveResponse(PleosResolveStatus.REGISTER, false, null, List.of());
		}
		if (mine.size() == 1) {
			Vehicle only = mine.getFirst();
			attachVin(userId, only, vinHash);
			return new PleosResolveResponse(PleosResolveStatus.LINKED, true, VehicleResponse.from(only), List.of());
		}
		return new PleosResolveResponse(PleosResolveStatus.SELECT, false, null,
				mine.stream().map(VehicleResponse::from).toList());
	}

	/** SELECT에서 고른 차량에 이 트럭을 연결하고 기본 차량으로 지정한다. */
	public VehicleResponse linkPleos(Long userId, Long vehicleId, PleosVinRequest request) {
		Vehicle vehicle = findMine(userId, vehicleId);
		attachVin(userId, vehicle, vinHasher.hash(request.vin()));
		return VehicleResponse.from(vehicle);
	}

	public VehicleResponse update(Long userId, Long vehicleId, UpdateVehicleRequest request) {
		Vehicle vehicle = findMine(userId, vehicleId);
		vehicle.changeSpec(request.tonnage(), request.bodyType(), request.heightM());
		return VehicleResponse.from(vehicle);
	}

	/** 차량 앱이 운행이 끝나거나 시동을 끌 때 Pleos 누적 연비를 올린다 (VEH-003). 말이 안 되는 값은 무시하고 지금 값을 돌려준다. */
	public VehicleResponse updateFuelEconomy(Long userId, Long vehicleId, FuelEconomyRequest request) {
		Vehicle vehicle = findMine(userId, vehicleId);
		vehicle.updateFuelEconomy(request.litersPer100Km(), clock.instant());
		return VehicleResponse.from(vehicle);
	}

	public VehicleResponse changeDefault(Long userId, Long vehicleId) {
		Vehicle vehicle = findMine(userId, vehicleId);
		makeDefault(userId, vehicle);
		return VehicleResponse.from(vehicle);
	}

	/** TODO: 운행 도메인이 생기면 진행 중인 운행에 쓰는 차량은 지우지 못하게 막는다 */
	public void delete(Long userId, Long vehicleId) {
		Vehicle vehicle = findMine(userId, vehicleId);
		boolean wasDefault = vehicle.isDefaultVehicle();
		vehicleRepository.delete(vehicle);
		vehicleRepository.flush();
		if (wasDefault) {
			vehicleRepository.findAllByDriverUserIdOrderByCreatedAtAsc(userId).stream()
					.findFirst()
					.ifPresent(Vehicle::markDefault);
		}
	}

	/**
	 * 이 VIN이 다른 차주 차량에 연결돼 있으면 409. 내 다른 차량에 연결돼 있으면 그쪽을 풀고 옮긴다
	 * (번호판을 바꿔 같은 트럭을 다시 등록한 경우).
	 */
	private void attachVin(Long userId, Vehicle vehicle, String vinHash) {
		vehicleRepository.findByVinHash(vinHash)
				.filter(existing -> !existing.getId().equals(vehicle.getId()))
				.ifPresent(existing -> {
					if (!existing.isOwnedBy(userId)) {
						throw new BusinessException(ErrorCode.VEHICLE_VIN_OTHER_OWNER);
					}
					existing.linkPleos(null);
					// vin_hash 유니크 제약 때문에 먼저 풀어 둔다
					vehicleRepository.flush();
				});
		vehicle.linkPleos(vinHash);
		makeDefault(userId, vehicle);
	}

	private void makeDefault(Long userId, Vehicle vehicle) {
		vehicleRepository.findByDriverUserIdAndDefaultVehicleTrue(userId)
				.filter(current -> !current.getId().equals(vehicle.getId()))
				.ifPresent(Vehicle::unmarkDefault);
		vehicle.markDefault();
	}

	/** 차주 프로필이 없으면 주선사 담당자 등 차주가 아닌 계정이다. 탈퇴한 계정은 없는 계정으로 본다. */
	private Driver requireDriver(Long userId) {
		Driver driver = driverRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_DRIVER));
		if (driver.getUser().getStatus() == UserStatus.WITHDRAWN) {
			throw new BusinessException(ErrorCode.USER_NOT_FOUND);
		}
		return driver;
	}

	/** 남의 차량이면 있는지도 알려 주지 않도록 404 */
	private Vehicle findMine(Long userId, Long vehicleId) {
		requireDriver(userId);
		return vehicleRepository.findById(vehicleId)
				.filter(vehicle -> vehicle.isOwnedBy(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND));
	}
}

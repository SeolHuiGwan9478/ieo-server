package com.ieo.server.domain.vehicle.service;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.domain.vehicle.dto.CreateVehicleRequest;
import com.ieo.server.domain.vehicle.dto.UpdateVehicleRequest;
import com.ieo.server.domain.vehicle.dto.VehicleResponse;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.repository.VehicleRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 차량 등록·관리 (VEH-001). 차주 계정만 쓸 수 있다.
 * 기본 차량은 차주당 하나: 첫 차량이 기본이 되고, 기본 차량을 지우면 남은 것 중 먼저 등록한 차량이 기본이 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class VehicleService {

	private final VehicleRepository vehicleRepository;
	private final DriverRepository driverRepository;

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
			return VehicleResponse.from(vehicleRepository.saveAndFlush(vehicle));
		} catch (DataIntegrityViolationException e) {
			// 같은 번호를 동시에 등록한 경우. plate_number 유니크 제약이 하나만 남긴다
			throw new BusinessException(ErrorCode.VEHICLE_DUPLICATE_PLATE);
		}
	}

	public VehicleResponse update(Long userId, Long vehicleId, UpdateVehicleRequest request) {
		Vehicle vehicle = findMine(userId, vehicleId);
		vehicle.changeSpec(request.tonnage(), request.bodyType(), request.heightM());
		return VehicleResponse.from(vehicle);
	}

	public VehicleResponse changeDefault(Long userId, Long vehicleId) {
		Vehicle vehicle = findMine(userId, vehicleId);
		vehicleRepository.findByDriverUserIdAndDefaultVehicleTrue(userId)
				.filter(current -> !current.getId().equals(vehicleId))
				.ifPresent(Vehicle::unmarkDefault);
		vehicle.markDefault();
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

package com.ieo.server.domain.driver.service;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.driver.dto.BusinessInfoResponse;
import com.ieo.server.domain.driver.dto.UpdateBusinessInfoRequest;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 차주 사업자 정보 (ACC-003). 선택 입력이고 장부 내보내기에 쓴다 */
@Service
@Transactional
@RequiredArgsConstructor
public class BusinessInfoService {

	private final DriverRepository driverRepository;

	@Transactional(readOnly = true)
	public BusinessInfoResponse get(Long userId) {
		return BusinessInfoResponse.from(findDriver(userId));
	}

	public BusinessInfoResponse update(Long userId, UpdateBusinessInfoRequest request) {
		Driver driver = findDriver(userId);
		driver.changeBusinessInfo(request.businessName().strip(), Broker.normalizeBusinessNumber(request.businessNumber()),
				request.representativeName().strip());
		return BusinessInfoResponse.from(driver);
	}

	public void clear(Long userId) {
		findDriver(userId).clearBusinessInfo();
	}

	private Driver findDriver(Long userId) {
		return driverRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_DRIVER));
	}
}

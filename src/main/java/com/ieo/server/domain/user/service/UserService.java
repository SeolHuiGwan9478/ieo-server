package com.ieo.server.domain.user.service;

import com.ieo.server.domain.broker.repository.BrokerInvitationRepository;
import com.ieo.server.domain.broker.service.BrokerMemberService;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.site.repository.SiteAttributeRepository;
import com.ieo.server.domain.site.repository.SiteNoteRepository;
import com.ieo.server.domain.site.repository.SiteRepository;
import com.ieo.server.domain.user.dto.UpdateUserRequest;
import com.ieo.server.domain.user.dto.UserResponse;
import com.ieo.server.domain.user.dto.WithdrawRequest;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.domain.user.repository.PhoneTokenRepository;
import com.ieo.server.domain.user.repository.PhoneVerificationRepository;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository userRepository;
	private final DriverRepository driverRepository;
	private final PhoneVerificationRepository phoneVerificationRepository;
	private final PhoneTokenRepository phoneTokenRepository;
	private final SiteRepository siteRepository;
	private final SiteAttributeRepository siteAttributeRepository;
	private final SiteNoteRepository siteNoteRepository;
	private final BrokerMemberService brokerMemberService;
	private final BrokerInvitationRepository brokerInvitationRepository;
	private final Clock clock;

	public UserResponse getUser(Long userId) {
		return UserResponse.from(findUser(userId));
	}

	@Transactional
	public UserResponse updateUser(Long userId, UpdateUserRequest request) {
		User user = findUser(userId);
		user.changeName(request.name());
		return UserResponse.from(user);
	}

	/**
	 * 탈퇴 (ACC-004). 계정 행은 운행·장부 기록과의 연결 때문에 남기고 개인정보만 지운다.
	 * 번호가 비워지므로 같은 번호로 다시 가입할 수 있다.
	 * 이미 발급된 액세스 토큰은 만료(30분)까지 남지만 탈퇴한 계정은 없는 계정으로 보고, 리프레시는 막힌다.
	 *
	 * 남기는 것: 동의 이력 (동의 받았다는 증빙, 번호·이름 없이 계정 ID에만 묶임),
	 *  주선사에게 이미 전달된 증빙 (주선사 쪽 보관 정책을 따른다),
	 *  상하차지·정보·메모 (다른 차주에게 쓸모 있는 현장 정보라 남기고, 작성자 연결만 끊는다),
	 *  주선사 담당자 기록 (오더의 등록 담당자라서 남기고 회사에서 나간 것으로 처리. 마지막 대표는 탈퇴할 수 없다)
	 * TODO: 운행 도메인이 생기면 위치 기록(LocationPoint)도 여기서 지운다
	 */
	@Transactional
	public void withdraw(Long userId, WithdrawRequest request) {
		User user = findUser(userId);
		String phone = user.getPhone();

		// 마지막 대표면 여기서 막혀 아무것도 지우지 않는다
		brokerMemberService.leaveOnWithdraw(userId);
		user.withdraw(request == null ? null : request.reason(), clock.instant());
		driverRepository.findById(userId).ifPresent(Driver::clearPersonalInfo);
		siteRepository.detachCreator(userId);
		siteAttributeRepository.detachReporter(userId);
		siteNoteRepository.detachAuthor(userId);
		if (phone != null) {
			phoneVerificationRepository.deleteByPhone(phone);
			phoneTokenRepository.deleteByPhone(phone);
			brokerInvitationRepository.deleteByPhone(phone);
		}
	}

	/** 탈퇴한 계정은 없는 계정으로 본다. */
	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.filter(user -> user.getStatus() != UserStatus.WITHDRAWN)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
	}
}

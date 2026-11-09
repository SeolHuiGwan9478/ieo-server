package com.ieo.server.domain.user.service;

import com.ieo.server.domain.user.dto.ConsentResponse;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.entity.UserConsent;
import com.ieo.server.domain.user.enums.ConsentType;
import com.ieo.server.domain.user.repository.UserConsentRepository;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 동의 관리 (ACC-006). 바꿀 때마다 이력을 한 행 더 쌓는다 */
@Service
@Transactional
@RequiredArgsConstructor
public class ConsentService {

	/** 이전 동의가 없는 항목에 쓸 약관 버전. 앱이 가입 때 보내는 값과 같다 */
	private static final String CURRENT_VERSION = "2026-11";

	private final UserConsentRepository userConsentRepository;
	private final UserRepository userRepository;

	@Transactional(readOnly = true)
	public List<ConsentResponse> list(Long userId) {
		User user = findUser(userId);
		Map<ConsentType, UserConsent> latest = userConsentRepository.findLatestByUserId(userId).stream()
				.collect(Collectors.toMap(UserConsent::getType, Function.identity()));
		return Arrays.stream(ConsentType.values())
				.map(type -> toResponse(user, type, latest.get(type)))
				.toList();
	}

	@Transactional(readOnly = true)
	public boolean isAgreed(Long userId, ConsentType type) {
		return userConsentRepository.findFirstByUserIdAndTypeOrderByCreatedAtDescIdDesc(userId, type)
				.map(UserConsent::isAgreed)
				.orElse(false);
	}

	/** [userIds] 중 지금 동의한 사람 */
	@Transactional(readOnly = true)
	public Set<Long> agreedAmong(Collection<Long> userIds, ConsentType type) {
		if (userIds.isEmpty()) {
			return Set.of();
		}
		return userConsentRepository.findLatestByUserIdInAndType(userIds, type).stream()
				.filter(UserConsent::isAgreed)
				.map(consent -> consent.getUser().getId())
				.collect(Collectors.toSet());
	}

	/** 필수 항목은 철회할 수 없다. 지금 상태와 같으면 이력을 쌓지 않는다 */
	public ConsentResponse change(Long userId, ConsentType type, boolean agreed) {
		User user = findUser(userId);
		if (!agreed && type.isRequiredFor(user.getRole())) {
			throw new BusinessException(ErrorCode.CONSENT_REQUIRED);
		}
		Optional<UserConsent> latest = userConsentRepository.findFirstByUserIdAndTypeOrderByCreatedAtDescIdDesc(userId, type);
		if (latest.map(UserConsent::isAgreed).orElse(false) == agreed) {
			return toResponse(user, type, latest.orElse(null));
		}
		String version = latest.map(UserConsent::getVersion).orElse(CURRENT_VERSION);
		return toResponse(user, type, userConsentRepository.save(UserConsent.of(user, type, version, agreed)));
	}

	private ConsentResponse toResponse(User user, ConsentType type, UserConsent consent) {
		return new ConsentResponse(type, type.getDescription(), type.isRequiredFor(user.getRole()),
				consent != null && consent.isAgreed(),
				consent == null ? null : consent.getVersion(),
				consent == null ? null : consent.getCreatedAt());
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
	}
}

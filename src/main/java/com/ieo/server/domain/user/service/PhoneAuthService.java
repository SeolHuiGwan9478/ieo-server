package com.ieo.server.domain.user.service;

import com.ieo.server.domain.user.dto.ExistingAccountResponse;
import com.ieo.server.domain.user.dto.PhoneVerifyResponse;
import com.ieo.server.domain.user.dto.VerifyCodeRequest;
import com.ieo.server.domain.user.entity.PhoneToken;
import com.ieo.server.domain.user.entity.PhoneVerification;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.repository.PhoneTokenRepository;
import com.ieo.server.domain.user.repository.PhoneVerificationRepository;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import com.ieo.server.global.sms.SmsSender;
import com.ieo.server.global.util.Hashes;
import com.ieo.server.global.util.RandomTokens;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 휴대폰 문자 인증. 인증번호를 보내고 확인해서 phoneToken을 발급한다.
 * 실제 로그인·가입은 AuthService가 phoneToken을 받아서 한다.
 *
 * 틀린 횟수·잠금은 오류를 돌려줄 때도 저장해야 해서 BusinessException으로는 롤백하지 않는다.
 *
 * TODO: 운영 시 Redis로 변경 예정. 인증번호·발송 횟수·잠금·phoneToken을 TTL 키로 두고
 *  (SET NX로 재발송 제한, INCR로 횟수, GETDEL로 phoneToken 한 번만 쓰기) phone_verifications·phone_tokens 테이블은 없앤다.
 */
@Service
@RequiredArgsConstructor
@Transactional(noRollbackFor = BusinessException.class)
public class PhoneAuthService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final PhoneVerificationRepository phoneVerificationRepository;
	private final PhoneTokenRepository phoneTokenRepository;
	private final UserRepository userRepository;
	private final SmsSender smsSender;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	/** 가입 여부와 상관없이 같은 응답을 준다. 번호만으로 가입 여부를 알 수 없게. */
	public void sendCode(String phone) {
		Instant now = clock.instant();
		PhoneVerification verification = phoneVerificationRepository.findByPhoneForUpdate(phone)
				.orElseGet(() -> phoneVerificationRepository.save(PhoneVerification.create(phone)));

		String code = "%06d".formatted(random.nextInt(1_000_000));
		verification.issueCode(Hashes.sha256Hex(phone + ":" + code), now, LocalDate.ofInstant(now, KST));
		smsSender.send(phone, "[이에오] 인증번호 " + code + " (3분 안에 입력해 주세요)");
	}

	/** 인증번호가 맞으면 phoneToken을 발급하고, 이 번호로 가입한 계정이 있는지 알려 준다. */
	public PhoneVerifyResponse verifyCode(VerifyCodeRequest request) {
		Instant now = clock.instant();
		PhoneVerification verification = phoneVerificationRepository.findByPhoneForUpdate(request.phone())
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_CODE_EXPIRED));
		verification.verify(Hashes.sha256Hex(request.phone() + ":" + request.code()), now);

		String rawToken = RandomTokens.urlSafe();
		PhoneToken phoneToken = phoneTokenRepository.save(
				PhoneToken.issue(Hashes.sha256Hex(rawToken), request.phone(), request.deviceId(), now));

		Optional<User> user = userRepository.findByPhone(request.phone());
		return new PhoneVerifyResponse(
				rawToken,
				phoneToken.getExpiresAt(),
				user.isPresent(),
				user.map(ExistingAccountResponse::from).orElse(null));
	}

	/**
	 * phoneToken을 쓰고 지운다. 없거나, 이미 썼거나, 만료됐거나, 다른 기기면 401.
	 * 인증된 번호를 돌려준다. 호출한 쪽 트랜잭션이 롤백되면 토큰도 되살아난다.
	 */
	public String consumePhoneToken(String rawToken, String deviceId) {
		String tokenHash = Hashes.sha256Hex(rawToken);
		PhoneToken phoneToken = phoneTokenRepository.findById(tokenHash)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_PHONE_TOKEN_INVALID));
		if (phoneTokenRepository.deleteByTokenHash(tokenHash) == 0
				|| !phoneToken.isUsableBy(deviceId, clock.instant())) {
			throw new BusinessException(ErrorCode.AUTH_PHONE_TOKEN_INVALID);
		}
		return phoneToken.getPhone();
	}
}

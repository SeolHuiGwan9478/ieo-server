package com.ieo.server.domain.dispatch.entity;

import com.ieo.server.domain.dispatch.enums.CancelReason;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 배차 확정 뒤 취소 기록 (DSP-030, 031). Ieo 배차로 생긴 운행에만 남는다.
 * 오더는 trip.order, 취소 시각은 createdAt, 차주·주선사 중 누가 취소했는지는 canceledBy의 역할로 안다.
 * 상차 예정 2시간 이내 취소는 이행 기록에 남는다(금전 패널티는 없다).
 */
@Getter
@Entity
@Table(name = "cancellations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cancellation extends BaseTimeEntity {

	/** 상차 예정 이 시간 안에 취소하면 늦은 취소 */
	public static final Duration LATE_WINDOW = Duration.ofHours(2);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trip_id", unique = true)
	private Trip trip;

	/** 취소한 사람 (차주 또는 주선사 담당자) */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "canceled_by")
	private User canceledBy;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private CancelReason reason;

	/** 사유 보충 (선택) */
	@Column(length = 200)
	private String memo;

	private Cancellation(Trip trip, User canceledBy, CancelReason reason, String memo) {
		this.trip = trip;
		this.canceledBy = canceledBy;
		this.reason = reason;
		this.memo = memo;
	}

	public static Cancellation of(Trip trip, User canceledBy, CancelReason reason, String memo) {
		String trimmed = memo == null || memo.isBlank() ? null : memo.strip();
		return new Cancellation(trip, canceledBy, reason, trimmed);
	}

	/** 상차 예정 2시간 이내(또는 지난 뒤) 취소인지. 예정 시각이 없으면 아니다 */
	public boolean isLate() {
		Instant plannedAt = trip.pickup().getPlannedAt();
		return plannedAt != null && getCreatedAt() != null && !getCreatedAt().isBefore(plannedAt.minus(LATE_WINDOW));
	}
}

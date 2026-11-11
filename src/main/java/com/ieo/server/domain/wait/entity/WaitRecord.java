package com.ieo.server.domain.wait.entity;

import com.ieo.server.domain.trip.entity.TripStop;
import com.ieo.server.domain.wait.enums.WaitStatus;
import com.ieo.server.global.entity.BaseTimeEntity;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상하차지 방문 한 번의 현장 기록 (02 대기). 진입 → 작업 시작 → 이탈 시각과 수정 이력을 가진다.
 * TripStop은 계획(어디에 언제), WaitRecord는 실제(언제 와서 언제 시작하고 언제 떠났나).
 *
 * 저장하는 것은 시각과 정책뿐이고, 상태·대기 시간·대기비는 매번 계산한다.
 * 대기비는 상차 예정 시각보다 일찍 온 시간을 빼고 센다 (일찍 와서 부풀리기 방지).
 * 작업 시작 시각이 없으면 대기와 작업을 나눌 수 없어 대기비를 계산하지 않는다.
 */
@Getter
@Entity
@Table(name = "wait_records")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitRecord extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trip_stop_id", unique = true)
	private TripStop tripStop;

	/** 진입: 지오펜스 안에서 기어 P나 속도 0이 3분 넘게 이어진 시각 (WAIT-001) */
	@Column(nullable = false)
	private Instant arrivedAt;

	/** 작업 시작: "상차 시작"·"하차 시작" 음성이나 버튼 (WAIT-004) */
	private Instant workStartedAt;

	/** 이탈: 지오펜스 밖으로 나가거나 시속 20km 이상으로 1분 넘게 달린 시각 (WAIT-005) */
	private Instant departedAt;

	/** 진입할 때 붙인 운행 주선사의 정책. 없으면 시간만 기록한다 (WAIT-011) */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "wait_policy_id")
	private WaitPolicy policy;

	/** 처음 고치기 전 값 (WAIT-006). 처음 고칠 때 세 값을 한꺼번에 남긴다 */
	private Instant originalArrivedAt;

	private Instant originalWorkStartedAt;

	private Instant originalDepartedAt;

	/** 마지막으로 고친 시각. 원래 값이 비어 있던 경우(작업 시작을 나중에 넣음)도 고친 기록으로 알 수 있다 */
	private Instant editedAt;

	/**
	 * 기사님이 확인하거나 누른 순간들 (WAIT-025). 시각을 고쳐도 이 목록은 그대로 남아 처음 기록을 보여 준다.
	 * 이 기능 전에 만든 기록은 비어 있다
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private List<WaitEvent> events = new ArrayList<>();

	private WaitRecord(TripStop tripStop, Instant arrivedAt, WaitPolicy policy) {
		this.tripStop = tripStop;
		this.arrivedAt = arrivedAt;
		this.policy = policy;
	}

	public static WaitRecord start(TripStop tripStop, Instant arrivedAt, WaitPolicy policy) {
		if (tripStop == null || arrivedAt == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		return new WaitRecord(tripStop, arrivedAt, policy);
	}

	/** 대기를 끝내고 작업을 시작한다 */
	public void startWork(Instant now) {
		if (status() != WaitStatus.WAITING) {
			throw new BusinessException(ErrorCode.WAIT_INVALID_STATUS);
		}
		requireOrder(arrivedAt, now, null);
		this.workStartedAt = now;
	}

	/** 이탈. 작업 시작 없이 떠나면 체류 시간만 남는다 */
	public void depart(Instant now) {
		if (status() == WaitStatus.DONE) {
			throw new BusinessException(ErrorCode.WAIT_INVALID_STATUS);
		}
		requireOrder(arrivedAt, workStartedAt, now);
		this.departedAt = now;
	}

	/**
	 * 차주가 시각을 고친다 (WAIT-006). 진입 ≤ 작업 시작 ≤ 이탈 순서여야 한다.
	 * 처음 고칠 때만 원래 값을 남기므로, 여러 번 고쳐도 자동으로 기록된 값이 남는다.
	 */
	public void edit(Instant arrivedAt, Instant workStartedAt, Instant departedAt, Instant now) {
		if (arrivedAt == null) {
			throw new BusinessException(ErrorCode.WAIT_INVALID_TIME);
		}
		requireOrder(arrivedAt, workStartedAt, departedAt);
		if (editedAt == null) {
			this.originalArrivedAt = this.arrivedAt;
			this.originalWorkStartedAt = this.workStartedAt;
			this.originalDepartedAt = this.departedAt;
		}
		this.arrivedAt = arrivedAt;
		this.workStartedAt = workStartedAt;
		this.departedAt = departedAt;
		this.editedAt = now;
	}

	/** 확인하거나 누른 순간을 남긴다 */
	public void record(WaitEvent event) {
		List<WaitEvent> next = new ArrayList<>(events());
		next.add(event);
		// 목록을 바꿔 끼워야 JSON 칸이 바뀐 것을 안다
		this.events = next;
	}

	public List<WaitEvent> events() {
		return events == null ? List.of() : List.copyOf(events);
	}

	public WaitStatus status() {
		if (departedAt != null) {
			return WaitStatus.DONE;
		}
		return workStartedAt != null ? WaitStatus.WORKING : WaitStatus.WAITING;
	}

	public boolean isEdited() {
		return editedAt != null;
	}

	/** 대기비를 세기 시작하는 시각 = max(진입, 예정 시각). 예정 시각이 없으면 진입 시각 */
	public Instant chargeableFrom() {
		Instant plannedAt = tripStop.getPlannedAt();
		return plannedAt != null && plannedAt.isAfter(arrivedAt) ? plannedAt : arrivedAt;
	}

	/** 실제 대기 = 진입 ~ 작업 시작. 상하차지 평균 대기 통계에 쓴다. 작업 시작 전이면 null */
	public Long waitMinutes() {
		return minutesBetween(arrivedAt, workStartedAt);
	}

	/** 대기비 대상 = 대기비 시작 ~ 작업 시작. 예정 시각 뒤에 작업을 시작했으면 0 */
	public Long chargeableWaitMinutes() {
		if (workStartedAt == null) {
			return null;
		}
		return Math.max(0, minutesBetween(chargeableFrom(), workStartedAt));
	}

	/** 작업 = 작업 시작 ~ 이탈 */
	public Long workMinutes() {
		return minutesBetween(workStartedAt, departedAt);
	}

	/** 체류 = 진입 ~ 이탈. 작업 시작이 없어도 남는다 */
	public Long stayMinutes() {
		return minutesBetween(arrivedAt, departedAt);
	}

	/** 청구 가능 대기비(원). 정책이 없거나 작업 시작이 없으면 null */
	public Integer chargeableAmount() {
		Long minutes = chargeableWaitMinutes();
		if (policy == null || minutes == null) {
			return null;
		}
		return policy.calculate(minutes);
	}

	private static Long minutesBetween(Instant from, Instant to) {
		if (from == null || to == null) {
			return null;
		}
		return Duration.between(from, to).toMinutes();
	}

	private static void requireOrder(Instant arrivedAt, Instant workStartedAt, Instant departedAt) {
		Instant last = arrivedAt;
		for (Instant next : new Instant[] {workStartedAt, departedAt}) {
			if (next == null) {
				continue;
			}
			if (next.isBefore(last)) {
				throw new BusinessException(ErrorCode.WAIT_INVALID_TIME);
			}
			last = next;
		}
	}
}

package com.ieo.server.domain.dispatch.entity;

import com.ieo.server.domain.dispatch.enums.ReportType;
import com.ieo.server.domain.driver.entity.Driver;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 차주의 오더 신고 (DSP-042). 차주 한 명이 오더 하나에 한 번만 신고한다.
 * 주선사에게 신고자는 알리지 않는다. 처리 상태는 운영자 화면을 만들 때 붙인다.
 */
@Getter
@Entity
@Table(name = "order_reports", uniqueConstraints = @UniqueConstraint(
		name = "uk_order_reports_order_reporter", columnNames = {"order_id", "reporter_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderReport extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id")
	private Order order;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reporter_id")
	private Driver reporter;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private ReportType type;

	/** 상황 설명 (선택) */
	@Column(length = 500)
	private String memo;

	private OrderReport(Order order, Driver reporter, ReportType type, String memo) {
		this.order = order;
		this.reporter = reporter;
		this.type = type;
		this.memo = memo;
	}

	public static OrderReport of(Order order, Driver reporter, ReportType type, String memo) {
		String trimmed = memo == null || memo.isBlank() ? null : memo.strip();
		return new OrderReport(order, reporter, type, trimmed);
	}
}

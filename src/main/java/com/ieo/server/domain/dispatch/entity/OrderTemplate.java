package com.ieo.server.domain.dispatch.entity;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.dispatch.enums.CargoCategory;
import com.ieo.server.domain.dispatch.enums.ConsignmentType;
import com.ieo.server.domain.dispatch.enums.LoadingMethod;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import com.ieo.server.global.entity.BaseTimeEntity;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자주 쓰는 노선 (DSP-004). 상하차지와 화물·차량·운임을 저장해 두고, 오더를 올릴 때 불러와 시간만 넣는다.
 * 회사 단위로 저장해 담당자끼리 같이 쓴다. 시간은 오더마다 달라서 저장하지 않는다
 */
@Getter
@Entity
@Table(name = "order_templates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderTemplate extends BaseTimeEntity {

	static final int MAX_NAME = 40;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "broker_id")
	private Broker broker;

	@Column(nullable = false, length = MAX_NAME)
	private String name;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pickup_site_id")
	private Site pickupSite;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "dropoff_site_id")
	private Site dropoffSite;

	@Enumerated(EnumType.STRING)
	private CargoCategory cargoCategory;

	private String cargoType;

	private Integer weightKg;

	@Enumerated(EnumType.STRING)
	private Tonnage tonnage;

	@Enumerated(EnumType.STRING)
	private BodyType bodyType;

	@Enumerated(EnumType.STRING)
	private LoadingMethod loadingMethod;

	private Integer fare;

	@Enumerated(EnumType.STRING)
	private PaymentType paymentType;

	private Integer expectedPaymentDays;

	@Enumerated(EnumType.STRING)
	private ConsignmentType consignmentType;

	private OrderTemplate(Broker broker, String name, Site pickupSite, Site dropoffSite) {
		this.broker = broker;
		this.name = name;
		this.pickupSite = pickupSite;
		this.dropoffSite = dropoffSite;
	}

	/** 이름과 상하차지는 필수. 나머지는 비워도 되고, 오더를 올릴 때 Order가 다시 검사한다 */
	public static OrderTemplate of(Broker broker, String name, Site pickupSite, Site dropoffSite, CargoCategory cargoCategory,
			String cargoType, Integer weightKg, Tonnage tonnage, BodyType bodyType, LoadingMethod loadingMethod, Integer fare,
			PaymentType paymentType, Integer expectedPaymentDays, ConsignmentType consignmentType) {
		String trimmed = name == null ? "" : name.trim();
		if (trimmed.isEmpty() || trimmed.length() > MAX_NAME) {
			throw new BusinessException(ErrorCode.ORDER_INVALID, "노선 이름은 1~" + MAX_NAME + "자로 적어 주세요.");
		}
		if (pickupSite.getId().equals(dropoffSite.getId())) {
			throw new BusinessException(ErrorCode.ORDER_INVALID, "상차지와 하차지가 같아요.");
		}
		OrderTemplate template = new OrderTemplate(broker, trimmed, pickupSite, dropoffSite);
		template.cargoCategory = cargoCategory;
		template.cargoType = cargoType;
		template.weightKg = weightKg;
		template.tonnage = tonnage;
		template.bodyType = bodyType;
		template.loadingMethod = loadingMethod;
		template.fare = fare;
		template.paymentType = paymentType;
		template.expectedPaymentDays = expectedPaymentDays;
		template.consignmentType = consignmentType;
		return template;
	}

	public boolean belongsTo(Long brokerId) {
		return broker.getId().equals(brokerId);
	}
}

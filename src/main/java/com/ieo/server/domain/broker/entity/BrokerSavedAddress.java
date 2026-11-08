package com.ieo.server.domain.broker.entity;

import com.ieo.server.domain.broker.dto.SaveBrokerAddressRequest;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 오더 사용 이력과 별개로 회사에서 명시적으로 저장한 주소. 상세주소까지 보관한다. */
@Getter
@Entity
@Table(name = "broker_saved_addresses", uniqueConstraints = @UniqueConstraint(
        name = "uk_broker_saved_address", columnNames = {"broker_id", "address", "detail_address"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrokerSavedAddress extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "broker_id")
    private Broker broker;

    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, length = 200)
    private String address;
    @Column(name = "detail_address", nullable = false, length = 100)
    private String detailAddress;
    @Column(nullable = false)
    private double lat;
    @Column(nullable = false)
    private double lng;

    public static BrokerSavedAddress create(Broker broker, SaveBrokerAddressRequest request) {
        String detail = request.detailAddress() == null ? "" : request.detailAddress().strip();
        String address = request.address().strip();
        if ((address + (detail.isEmpty() ? "" : " " + detail)).length() > 200
                || !Double.isFinite(request.lat()) || !Double.isFinite(request.lng())) {
            throw new BusinessException(ErrorCode.SAVED_ADDRESS_INVALID);
        }
        BrokerSavedAddress saved = new BrokerSavedAddress();
        saved.broker = broker;
        saved.name = request.name().strip();
        saved.address = address;
        saved.detailAddress = detail;
        saved.lat = request.lat();
        saved.lng = request.lng();
        return saved;
    }
}

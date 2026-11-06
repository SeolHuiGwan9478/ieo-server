package com.ieo.server.domain.broker.dto;

import com.ieo.server.domain.broker.enums.DashboardUnit;
import java.time.LocalDate;
import java.util.List;

/**
 * 콘솔 첫 화면 대시보드. 기간을 unit(일·주·월) 칸으로 나눠 오더 등록 수, 완료·취소 운행 수, 운임 합계, 대기비 합계를 센다.
 * 모든 플랜이 본다 (내 회사 숫자뿐이라서). 빈 칸도 0으로 들어가 그래프가 끊기지 않는다.
 *
 * 세는 기준: 오더는 등록한 날, 완료는 완료한 날, 취소는 취소한 날. 운임 합계는 그 칸에 완료된 운행의 확정 운임 (차주에게 줄 돈),
 * 대기비는 그 완료 운행들의 대기 증빙으로 계산된 금액. 실제 지급 완료 금액은 차주 장부라 여기 없다 (06 문서 보류).
 * 청구 합계(billedTotal)·수수료 합계(feeTotal)는 완료 운행 중 오더에 청구운임을 적은 것(pricedCount)만 더한다.
 * 수수료 = 청구운임 − 지급운임. 대기비는 화주에게 받아 차주에게 그대로 가는 돈이라 수수료에 넣지 않는다
 */
public record BrokerDashboardResponse(
		DashboardUnit unit,
		String unitLabel,
		LocalDate from,
		LocalDate to,
		Bucket total,
		List<Bucket> buckets) {

	/** 한 칸. start·end는 기간 안으로 잘린 날짜 (양 끝 포함) */
	public record Bucket(
			LocalDate start,
			LocalDate end,
			long orderCount,
			int completedCount,
			int canceledCount,
			long fareTotal,
			long waitFeeTotal,
			int pricedCount,
			long billedTotal,
			long feeTotal) {

		public static Bucket empty(LocalDate start, LocalDate end) {
			return new Bucket(start, end, 0, 0, 0, 0, 0, 0, 0, 0);
		}

		public Bucket plus(long orders, int completed, int canceled, long fare, long waitFee) {
			return new Bucket(start, end, orderCount + orders, completedCount + completed, canceledCount + canceled,
					fareTotal + fare, waitFeeTotal + waitFee, pricedCount, billedTotal, feeTotal);
		}

		/** 청구운임이 적힌 완료 운행 한 건 */
		public Bucket plusPriced(long billedFare, long fee) {
			return new Bucket(start, end, orderCount, completedCount, canceledCount, fareTotal, waitFeeTotal,
					pricedCount + 1, billedTotal + billedFare, feeTotal + fee);
		}

		public Bucket plus(Bucket other) {
			return new Bucket(start, end, orderCount + other.orderCount, completedCount + other.completedCount,
					canceledCount + other.canceledCount, fareTotal + other.fareTotal, waitFeeTotal + other.waitFeeTotal,
					pricedCount + other.pricedCount, billedTotal + other.billedTotal, feeTotal + other.feeTotal);
		}
	}
}

package com.ieo.server.domain.ledger.service;

import com.ieo.server.domain.broker.service.BrokerMetricsCalculator;
import com.ieo.server.domain.delivery.entity.DeliveryProof;
import com.ieo.server.domain.delivery.repository.DeliveryProofRepository;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.ledger.dto.CreateLedgerEntryRequest;
import com.ieo.server.domain.ledger.dto.DailySummaryResponse;
import com.ieo.server.domain.ledger.dto.ExtraIncomeResponse;
import com.ieo.server.domain.ledger.dto.LedgerEntryResponse;
import com.ieo.server.domain.ledger.dto.ReceivablesResponse;
import com.ieo.server.domain.ledger.dto.UpdateLedgerEntryRequest;
import com.ieo.server.domain.ledger.entity.Expense;
import com.ieo.server.domain.ledger.entity.ExtraIncome;
import com.ieo.server.domain.ledger.entity.LedgerEntry;
import com.ieo.server.domain.ledger.repository.ExpenseRepository;
import com.ieo.server.domain.ledger.repository.ExtraIncomeRepository;
import com.ieo.server.domain.ledger.repository.LedgerEntryRepository;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.domain.wait.entity.WaitRecord;
import com.ieo.server.domain.wait.repository.WaitRecordRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import com.ieo.server.global.util.KoreanDate;
import com.ieo.server.domain.ledger.entity.TaxInvoice;
import com.ieo.server.domain.ledger.repository.TaxInvoiceRepository;
import com.ieo.server.domain.plan.enums.DriverFeature;
import com.ieo.server.domain.plan.service.DriverPlanService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장부 (06 장부): 기간 장부, 직접 추가·고치기, 받음 표시, 미수금, 하루 요약. 차주 계정만 쓸 수 있다.
 * 날짜는 한국 날짜 기준이다.
 * TODO: 플랜별 보기 범위(베이직은 이번 달)는 12 요금제를 만든 뒤 붙인다
 */
@Service
@RequiredArgsConstructor
@Transactional
public class LedgerService {

	static final int MAX_RANGE_DAYS = 366;

	private final LedgerEntryRepository entryRepository;
	private final ExtraIncomeRepository extraIncomeRepository;
	private final ExpenseRepository expenseRepository;
	private final WaitRecordRepository waitRecordRepository;
	private final DeliveryProofRepository deliveryProofRepository;
	private final DriverRepository driverRepository;
	private final BrokerMetricsCalculator metricsCalculator;
	private final Clock clock;
	private final TaxInvoiceRepository taxInvoiceRepository;
	private final DriverPlanService driverPlanService;

	/** 기간 장부. 비우면 이번 달. 최근 날짜 먼저 */
	@Transactional(readOnly = true)
	public List<LedgerEntryResponse> getEntries(Long userId, LocalDate from, LocalDate to) {
		requireDriver(userId);
		LocalDate today = KoreanDate.today(clock);
		LocalDate start = from != null ? from : today.withDayOfMonth(1);
		LocalDate end = to != null ? to : today.withDayOfMonth(today.lengthOfMonth());
		validateRange(start, end);
		if (start.isBefore(today.withDayOfMonth(1))) {
			driverPlanService.requireFeature(userId, DriverFeature.LEDGER_HISTORY); // 베이직은 이번 달만 (12장)
		}
		return toResponses(entryRepository.findAllByDriverUserIdAndWorkDateBetweenOrderByWorkDateDescIdDesc(userId, start, end));
	}

	/** 직접 추가 (LEDG-004) */
	public LedgerEntryResponse addManual(Long userId, CreateLedgerEntryRequest request) {
		Driver driver = requireDriver(userId);
		LedgerEntry entry = LedgerEntry.manual(driver, request.workDate(), request.description(),
				request.fare(), request.paymentType(), request.brokerName(), request.expectedPaymentDate());
		entry.changeDistance(request.distanceKm());
		return toResponse(entryRepository.save(entry));
	}

	/** 운임·결제 방식·지급 예정일·주행거리 고치기 (LEDG-003) */
	public LedgerEntryResponse update(Long userId, Long entryId, UpdateLedgerEntryRequest request) {
		LedgerEntry entry = findMine(userId, entryId);
		entry.edit(request.fare(), request.paymentType(), request.expectedPaymentDate());
		entry.changeDistance(request.distanceKm());
		metricsCalculator.recalculate(entry.getTrip());
		return toResponse(entry);
	}

	/** 직접 추가한 줄만 지운다. 운행으로 생긴 줄은 운행 기록과 맞아야 해서 고치기만 한다 */
	public void delete(Long userId, Long entryId) {
		LedgerEntry entry = findMine(userId, entryId);
		if (entry.getTrip() != null) {
			throw new BusinessException(ErrorCode.LEDGER_AUTO_ENTRY);
		}
		entryRepository.delete(entry);
	}

	/** 차량 앱: 운행이 끝날 때 주행거리를 넣는다 */
	public LedgerEntryResponse changeDistance(Long userId, Long tripId, Integer distanceKm) {
		requireDriver(userId);
		LedgerEntry entry = entryRepository.findByTripId(tripId)
				.filter(found -> found.isOwnedBy(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.LEDGER_NOT_FOUND));
		entry.changeDistance(distanceKm);
		return toResponse(entry);
	}

	/** 운임 받음 / 되돌리기 (LEDG-021) */
	public LedgerEntryResponse setPaid(Long userId, Long entryId, boolean paid) {
		LedgerEntry entry = findMine(userId, entryId);
		if (paid) {
			entry.markPaid(clock.instant());
		} else {
			entry.unmarkPaid();
		}
		metricsCalculator.recalculate(entry.getTrip());
		return toResponse(entry);
	}

	/** 대기비 받음 / 되돌리기 (LEDG-002) */
	public ExtraIncomeResponse setExtraIncomePaid(Long userId, Long extraIncomeId, boolean paid) {
		requireDriver(userId);
		ExtraIncome income = extraIncomeRepository.findById(extraIncomeId)
				.filter(found -> found.getLedgerEntry().isOwnedBy(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.LEDGER_NOT_FOUND));
		if (paid) {
			income.markPaid(clock.instant());
		} else {
			income.unmarkPaid();
		}
		metricsCalculator.recalculate(income.getLedgerEntry().getTrip());
		return ExtraIncomeResponse.from(income);
	}

	/** 미수금: 받지 못한 운임(지급 예정일 순)과 청구 예정 대기비 합계 */
	@Transactional(readOnly = true)
	public ReceivablesResponse getReceivables(Long userId) {
		requireDriver(userId);
		LocalDate today = KoreanDate.today(clock);
		List<LedgerEntry> unpaid = entryRepository.findAllByDriverUserIdAndPaidAtIsNullOrderByExpectedPaymentDateAsc(userId);
		long fareTotal = unpaid.stream().mapToLong(LedgerEntry::getFare).sum();
		long overdueTotal = unpaid.stream().filter(entry -> entry.isOverdue(today)).mapToLong(LedgerEntry::getFare).sum();
		long waitFeeTotal = extraIncomeRepository.findAllByLedgerEntryDriverUserIdAndPaidAtIsNull(userId).stream()
				.mapToLong(ExtraIncome::getAmount).sum();
		return new ReceivablesResponse(fareTotal, overdueTotal, waitFeeTotal, toResponses(unpaid));
	}

	/**
	 * 하루 요약 (LEDG-030). 비우면 오늘. 그날 운행일인 장부, 그날 쓴 경비, 그 운행들의 대기로 계산한다
	 */
	@Transactional(readOnly = true)
	public DailySummaryResponse getDailySummary(Long userId, LocalDate date) {
		requireDriver(userId);
		LocalDate day = date != null ? date : KoreanDate.today(clock);
		List<LedgerEntry> entries = entryRepository.findAllByDriverUserIdAndWorkDateBetweenOrderByWorkDateDescIdDesc(userId, day, day);
		List<ExtraIncome> incomes = entries.isEmpty() ? List.of()
				: extraIncomeRepository.findAllByLedgerEntryIdIn(entries.stream().map(LedgerEntry::getId).toList());
		List<Expense> expenses = expenseRepository.findAllByDriverUserIdAndSpentOnBetweenOrderBySpentOnDescIdDesc(userId, day, day);

		long fareTotal = entries.stream().mapToLong(LedgerEntry::getFare).sum();
		long waitFeeTotal = incomes.stream().mapToLong(ExtraIncome::getAmount).sum();
		long claimable = incomes.stream().filter(income -> !income.isPaid()).mapToLong(ExtraIncome::getAmount).sum();
		long expenseTotal = expenses.stream().mapToLong(Expense::getAmount).sum();
		long waitMinutes = entries.stream().map(this::waitMinutes).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
		return new DailySummaryResponse(day, entries.size(), fareTotal, waitFeeTotal, expenseTotal,
				fareTotal + waitFeeTotal - expenseTotal, waitMinutes, claimable);
	}

	private List<LedgerEntryResponse> toResponses(List<LedgerEntry> entries) {
		if (entries.isEmpty()) {
			return List.of();
		}
		LocalDate today = KoreanDate.today(clock);
		Map<Long, List<ExtraIncomeResponse>> incomes = extraIncomeRepository
				.findAllByLedgerEntryIdIn(entries.stream().map(LedgerEntry::getId).toList()).stream()
				.collect(Collectors.groupingBy(income -> income.getLedgerEntry().getId(),
						Collectors.mapping(ExtraIncomeResponse::from, Collectors.toList())));
		List<Long> tripIds = entries.stream().map(LedgerEntry::getTrip).filter(Objects::nonNull).map(Trip::getId).toList();
		Map<Long, Instant> receiptSentByTrip = tripIds.isEmpty() ? Map.of()
				: deliveryProofRepository.findAllByTripIdIn(tripIds).stream()
						.filter(DeliveryProof::isSent)
						.collect(Collectors.toMap(proof -> proof.getTrip().getId(), DeliveryProof::getSentAt));
		Map<Long, Instant> invoiceIssuedByEntry = taxInvoiceRepository
				.findAllByLedgerEntryIdIn(entries.stream().map(LedgerEntry::getId).toList()).stream()
				.collect(Collectors.toMap(invoice -> invoice.getLedgerEntry().getId(), TaxInvoice::getIssuedAt));
		return entries.stream()
				.map(entry -> LedgerEntryResponse.of(entry, today, waitMinutes(entry),
						entry.getTrip() == null ? null : receiptSentByTrip.get(entry.getTrip().getId()),
						invoiceIssuedByEntry.get(entry.getId()),
						incomes.getOrDefault(entry.getId(), List.of())))
				.toList();
	}

	private LedgerEntryResponse toResponse(LedgerEntry entry) {
		return toResponses(List.of(entry)).getFirst();
	}

	/** 운행의 상차·하차 실제 대기 합계. 직접 추가한 줄이거나 대기 기록이 없으면 null */
	private Long waitMinutes(LedgerEntry entry) {
		if (entry.getTrip() == null) {
			return null;
		}
		List<Long> minutes = waitRecordRepository.findAllByTripStopTripId(entry.getTrip().getId()).stream()
				.map(WaitRecord::waitMinutes)
				.filter(Objects::nonNull)
				.toList();
		return minutes.isEmpty() ? null : minutes.stream().mapToLong(Long::longValue).sum();
	}

	static void validateRange(LocalDate from, LocalDate to) {
		if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
			throw new BusinessException(ErrorCode.LEDGER_INVALID, "기간은 시작일이 끝일보다 앞서고 1년 이내여야 해요.");
		}
	}

	private LedgerEntry findMine(Long userId, Long entryId) {
		requireDriver(userId);
		return entryRepository.findById(entryId)
				.filter(entry -> entry.isOwnedBy(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.LEDGER_NOT_FOUND));
	}

	Driver requireDriver(Long userId) {
		Driver driver = driverRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.LEDGER_NOT_DRIVER));
		if (driver.getUser().getStatus() == UserStatus.WITHDRAWN) {
			throw new BusinessException(ErrorCode.USER_NOT_FOUND);
		}
		return driver;
	}
}

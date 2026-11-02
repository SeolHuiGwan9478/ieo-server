package com.ieo.server.domain.dispatch.dto;

import com.ieo.server.domain.dispatch.enums.ReportType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrderReportRequest(@NotNull ReportType type, @Size(max = 500) String memo) {
}

package com.ieo.server.domain.broker.dto;

import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import jakarta.validation.constraints.NotNull;

public record ChangePositionRequest(@NotNull BrokerMemberPosition position) {
}

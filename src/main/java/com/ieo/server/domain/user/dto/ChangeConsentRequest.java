package com.ieo.server.domain.user.dto;

import jakarta.validation.constraints.NotNull;

public record ChangeConsentRequest(@NotNull Boolean agreed) {
}

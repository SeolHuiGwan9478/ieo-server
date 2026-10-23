package com.ieo.server.domain.user.dto;

import com.ieo.server.domain.user.enums.ConsentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConsentRequest(@NotNull ConsentType type, @NotBlank @Size(max = 20) String version, boolean agreed) {
}

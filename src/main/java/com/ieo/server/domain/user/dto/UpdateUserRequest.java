package com.ieo.server.domain.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(@Size(max = 30) String name) {
}

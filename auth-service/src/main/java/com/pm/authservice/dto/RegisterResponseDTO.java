package com.pm.authservice.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class RegisterResponseDTO {
    private String username;
    private Instant createdAt;
}

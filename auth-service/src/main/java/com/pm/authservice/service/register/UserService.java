package com.pm.authservice.service.register;

import com.pm.authservice.dto.RegisterRequestDTO;
import com.pm.authservice.dto.RegisterResponseDTO;

public interface UserService {
    public RegisterResponseDTO register(RegisterRequestDTO registerRequestDTO);
}

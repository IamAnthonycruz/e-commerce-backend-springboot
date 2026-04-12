package com.pm.authservice.service;

import com.pm.authservice.dto.RegisterRequestDTO;
import com.pm.authservice.dto.RegisterResponseDTO;

public interface UserService {
    RegisterResponseDTO register(RegisterRequestDTO registerRequestDTO);

}

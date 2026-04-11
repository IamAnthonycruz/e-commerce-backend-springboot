package com.pm.authservice.mapper;

import com.pm.authservice.dto.RegisterRequestDTO;
import com.pm.authservice.dto.RegisterResponseDTO;
import com.pm.authservice.entity.User;
import org.springframework.stereotype.Component;

@Component
public class RegisterMapper implements Mapper<RegisterResponseDTO, RegisterRequestDTO, User> {


    @Override
    public RegisterResponseDTO toDto(User user) {
        RegisterResponseDTO registerResponseDTO = new RegisterResponseDTO();
        registerResponseDTO.setUsername(user.getUsername());
        registerResponseDTO.setCreatedAt(user.getCreatedAt());

        return registerResponseDTO;
    }

    @Override
    public User toEntity(RegisterRequestDTO registerRequestDTO) {
        User user = new User();
        user.setUsername(registerRequestDTO.getUsername());
        return user;
    }
}

package com.pm.authservice.service;

import com.pm.authservice.dto.RegisterRequestDTO;
import com.pm.authservice.dto.RegisterResponseDTO;
import com.pm.authservice.entity.User;
import com.pm.authservice.exception.DuplicateUsernameException;
import com.pm.authservice.mapper.RegisterMapper;
import com.pm.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RegisterMapper registerMapper;
    @Override
    public RegisterResponseDTO register(RegisterRequestDTO registerRequestDTO) {
        if (userRepository.findByUsername(registerRequestDTO.getUsername()).isPresent()){
            throw new DuplicateUsernameException("Duplicate username error");
        }
        User user = registerMapper.toEntity(registerRequestDTO);
        user.setPasswordHash(passwordEncoder.encode(registerRequestDTO.getPassword()));
        try{
            userRepository.save(user);
        } catch(DataIntegrityViolationException e) {
            throw new DuplicateUsernameException("Username already exists");
        }


        return registerMapper.toDto(user);
    }
}

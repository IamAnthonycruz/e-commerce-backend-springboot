package com.pm.authservice.service.register;

import com.pm.authservice.dto.RegisterRequestDTO;
import com.pm.authservice.dto.RegisterResponseDTO;
import com.pm.authservice.entity.User;
import com.pm.authservice.mapper.RegisterMapper;
import com.pm.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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
            throw new RuntimeException(); //Need to add global exception handling
        }
        User user = registerMapper.toEntity(registerRequestDTO);
        user.setPasswordHash(passwordEncoder.encode(registerRequestDTO.getPassword()));
        //userRepository.save(user);

        return registerMapper.toDto(user);
    }
}

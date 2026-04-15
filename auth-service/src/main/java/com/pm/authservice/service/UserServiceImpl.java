package com.pm.authservice.service;

import com.pm.authservice.dto.LoginRequestDTO;
import com.pm.authservice.dto.LoginResponseDTO;
import com.pm.authservice.dto.RegisterRequestDTO;
import com.pm.authservice.dto.RegisterResponseDTO;
import com.pm.authservice.entity.User;
import com.pm.authservice.exception.DuplicateUsernameException;
import com.pm.authservice.exception.InvalidCredentialException;
import com.pm.authservice.mapper.RegisterMapper;
import com.pm.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;


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

    @Override
    public LoginResponseDTO login(LoginRequestDTO loginRequestDTO) {
        Optional<User> user = userRepository.findByUsername(loginRequestDTO.getUsername());
        if (user.isEmpty()) {
               throw new UsernameNotFoundException("Username was not found");
        }
        if(!passwordEncoder.matches(loginRequestDTO.getPassword(), user.get().getPasswordHash())){
            throw new InvalidCredentialException("Invalid username or password");
        }

        LoginResponseDTO loginResponseDTO = new LoginResponseDTO();
        



    }

}

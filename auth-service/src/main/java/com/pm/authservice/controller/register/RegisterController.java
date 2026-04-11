package com.pm.authservice.controller.register;

import com.pm.authservice.dto.RegisterRequestDTO;
import com.pm.authservice.dto.RegisterResponseDTO;
import com.pm.authservice.service.register.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequiredArgsConstructor
public class RegisterController {
    private final UserService registerService;
    @PostMapping("/api/v1/auth/register")
    ResponseEntity<RegisterResponseDTO> register(@Valid @RequestBody RegisterRequestDTO registrationRequestDTO){
        RegisterResponseDTO response = registerService.register(registrationRequestDTO);
        return new ResponseEntity<>(new RegisterResponseDTO(), HttpStatus.CREATED);
    }
}

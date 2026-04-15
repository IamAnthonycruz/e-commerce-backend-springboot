package com.pm.authservice.exception;


import com.pm.authservice.dto.ErrorResponseDTO;
import com.pm.authservice.dto.FieldErrorDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO<List<FieldErrorDTO>>> handleValidationException(MethodArgumentNotValidException ex ){

        List<FieldErrorDTO> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(f -> new FieldErrorDTO(f.getField(), f.getDefaultMessage()))
                .toList();
       ErrorResponseDTO<List<FieldErrorDTO>> errorResponseDto = new ErrorResponseDTO<>();

       errorResponseDto.setStatus(HttpStatus.BAD_REQUEST.value());
       errorResponseDto.setMessage("Validation Failed for One or More Fields.");
       errorResponseDto.setTimestamp(LocalDateTime.now());
       errorResponseDto.setDetails(fieldErrors);
       return ResponseEntity.badRequest().body(errorResponseDto);
    }


    @ExceptionHandler(DuplicateUsernameException.class)
    public ResponseEntity<ErrorResponseDTO<Void>> handleDuplicateUsernameException(DuplicateUsernameException ex){
        ErrorResponseDTO<Void> errorResponseDto = new ErrorResponseDTO<>();
        errorResponseDto.setStatus(HttpStatus.CONFLICT.value());
        errorResponseDto.setMessage("Duplicate Username");
        errorResponseDto.setTimestamp(LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponseDto);
    }
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO<Void>> handleUsernameNotFoundException(UsernameNotFoundException ex) {
        ErrorResponseDTO<Void> errorResponseDTO = new ErrorResponseDTO<>();
        errorResponseDTO.setStatus(HttpStatus.UNAUTHORIZED.value());
        errorResponseDTO.setMessage("Username not found");
        errorResponseDTO.setTimestamp(LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponseDTO);
    }
    @ExceptionHandler(InvalidCredentialException.class)
    public ResponseEntity<ErrorResponseDTO<Void>> handleInvalidCredentialException(InvalidCredentialException ex) {
        ErrorResponseDTO<Void> errorResponseDTO = new ErrorResponseDTO<>();
        errorResponseDTO.setStatus(HttpStatus.UNAUTHORIZED.value());
        errorResponseDTO.setMessage("Username or Password is Incorrect");
        errorResponseDTO.setTimestamp(LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponseDTO);
    }
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponseDTO<Void>> handleGeneralException(RuntimeException ex){
        log.error("Unhandled runtime exception", ex);
        ErrorResponseDTO<Void> errorResponseDto = new ErrorResponseDTO<>();
        errorResponseDto.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        errorResponseDto.setMessage("Internal Server Error");
        errorResponseDto.setTimestamp(LocalDateTime.now());
        return ResponseEntity.internalServerError().body(errorResponseDto);
    }

}

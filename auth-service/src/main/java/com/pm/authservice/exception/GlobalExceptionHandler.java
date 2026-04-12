package com.pm.authservice.exception;


import com.pm.authservice.dto.ErrorResponseDto;
import com.pm.authservice.dto.FieldErrorDto;
import jakarta.validation.constraints.Null;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto<List<FieldErrorDto>>> handleValidationException(MethodArgumentNotValidException ex ){

        List<FieldErrorDto> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(f -> new FieldErrorDto(f.getField(), f.getDefaultMessage()))
                .toList();
       ErrorResponseDto<List<FieldErrorDto>> errorResponseDto = new ErrorResponseDto<>();

       errorResponseDto.setStatus(HttpStatus.BAD_REQUEST.value());
       errorResponseDto.setMessage("Validation failed for one or more fields.");
       errorResponseDto.setTimestamp(LocalDateTime.now());
       errorResponseDto.setDetails(fieldErrors);
       return ResponseEntity.badRequest().body(errorResponseDto);
    }


    @ExceptionHandler(DuplicateUsernameException.class)
    public ResponseEntity<ErrorResponseDto<Void>> handleDuplicateUsernameException(DuplicateUsernameException ex){
        ErrorResponseDto<Void> errorResponseDto = new ErrorResponseDto<>();
        errorResponseDto.setStatus(HttpStatus.CONFLICT.value());
        errorResponseDto.setMessage("Duplicate Username");
        errorResponseDto.setTimestamp(LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponseDto);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponseDto<Void>> handleGeneralException(RuntimeException ex){
        log.error("Unhandled runtime exception", ex);
        ErrorResponseDto<Void> errorResponseDto = new ErrorResponseDto<>();
        errorResponseDto.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        errorResponseDto.setMessage("Internal Server Error");
        errorResponseDto.setTimestamp(LocalDateTime.now());
        return ResponseEntity.internalServerError().body(errorResponseDto);
    }
}

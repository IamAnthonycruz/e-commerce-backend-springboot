package com.pm.authservice.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ErrorResponseDto<T> {
   private int status;
   private String message;
   private LocalDateTime timestamp;

   @JsonInclude(JsonInclude.Include.NON_NULL)
    private T details;
}

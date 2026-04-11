package com.pm.authservice.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordValidator implements ConstraintValidator<StrongPassword, String> {
    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        String sanitizedPassword = password.strip();
        if(sanitizedPassword.isBlank()) {
            return false;
        }
        boolean hasLength = sanitizedPassword.length() >=8;
        boolean hasLetter = sanitizedPassword.matches(".*[a-zA-Z].*");
        boolean hasDigit = sanitizedPassword.matches(".*[0-9].*");
        return hasLength && hasLetter && hasDigit;
    }
}

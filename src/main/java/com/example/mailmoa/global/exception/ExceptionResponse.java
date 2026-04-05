package com.example.mailmoa.global.exception;

public record ExceptionResponse(
        int status,
        String message
) {}

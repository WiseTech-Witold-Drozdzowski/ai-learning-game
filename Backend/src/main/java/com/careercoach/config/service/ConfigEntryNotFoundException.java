package com.careercoach.config.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ConfigEntryNotFoundException extends RuntimeException {

    public ConfigEntryNotFoundException(String message) {
        super(message);
    }
}

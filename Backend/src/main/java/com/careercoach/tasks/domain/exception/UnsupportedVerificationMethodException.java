package com.careercoach.tasks.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.careercoach.config.domain.VerificationMethod;

@ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
public class UnsupportedVerificationMethodException extends RuntimeException {

    public UnsupportedVerificationMethodException(String message) {
        super(message);
    }

    public UnsupportedVerificationMethodException(VerificationMethod method) {
        this("Verification method " + method + " is out of scope in this version");
    }
}

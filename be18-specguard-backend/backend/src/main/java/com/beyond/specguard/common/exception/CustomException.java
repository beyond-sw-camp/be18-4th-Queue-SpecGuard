package com.beyond.specguard.common.exception;

import com.beyond.specguard.common.exception.errorcode.ErrorCode;
import lombok.Getter;

import java.util.Map;

@Getter
public class CustomException extends RuntimeException {
    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}

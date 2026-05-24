package com.demo.handler;

import com.demo.vo.R;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public R handleException(Exception e) {
        return R.error(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R handleValidationException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldError().getDefaultMessage();
        return R.error(msg);
    }
}

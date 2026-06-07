package com.demo.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RegisterRequest {
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的手机号")
    private String username;
    private String password;
    private String confirmPassword;
    private String displayName;
}

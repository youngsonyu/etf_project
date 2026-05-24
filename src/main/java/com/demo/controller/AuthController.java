package com.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.dto.LoginRequest;
import com.demo.dto.RegisterRequest;
import com.demo.entity.SysUser;
import com.demo.entity.SysParam;
import com.demo.service.SysUserService;
import com.demo.service.SysParamService;
import com.demo.utils.PasswordUtils;
import com.demo.vo.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private SysParamService sysParamService;

    @Autowired
    private SysUserService sysUserService;

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody LoginRequest payload) {
        String username = payload == null || payload.getUsername() == null ? "" : payload.getUsername().trim();
        String password = payload == null || payload.getPassword() == null ? "" : payload.getPassword().trim();
        String loginType = payload == null || payload.getLoginType() == null ? "admin" : payload.getLoginType().trim();

        if (username.isEmpty() || password.isEmpty()) {
            return R.error("账号和密码不能为空");
        }

        if ("user".equalsIgnoreCase(loginType)) {
            return userLogin(username, password);
        }

        return adminLogin(username, password);
    }

    @PostMapping("/register")
    public R<Boolean> register(@RequestBody RegisterRequest payload) {
        if (!isUserRegisterEnabled()) {
            return R.error("当前未开放新用户注册，请联系管理员");
        }

        String username = payload == null || payload.getUsername() == null ? "" : payload.getUsername().trim();
        String password = payload == null || payload.getPassword() == null ? "" : payload.getPassword().trim();
        String confirmPassword = payload == null || payload.getConfirmPassword() == null ? "" : payload.getConfirmPassword().trim();
        String displayName = payload == null || payload.getDisplayName() == null ? "" : payload.getDisplayName().trim();

        if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            return R.error("账号、密码、确认密码不能为空");
        }

        if (!username.matches("[A-Za-z0-9_]{4,32}")) {
            return R.error("账号需为4到32位字母、数字或下划线");
        }

        if (password.length() < 6 || password.length() > 64) {
            return R.error("密码长度需在6到64位之间");
        }

        if (!password.equals(confirmPassword)) {
            return R.error("两次输入的密码不一致");
        }

        String configuredUsername = readParamValue("auth.admin.username");
        if (username.equals(configuredUsername)) {
            return R.error("该账号已被管理员占用");
        }

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("LIMIT 1");
        if (sysUserService.getOne(wrapper, false) != null) {
            return R.error("账号已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(PasswordUtils.hash(password));
        user.setDisplayName(displayName.isEmpty() ? username : displayName);
        user.setIsActive(1);
        return R.ok(sysUserService.save(user));
    }

    @GetMapping("/register-enabled")
    public R<Map<String, Object>> registerEnabled() {
        boolean enabled = isUserRegisterEnabled();
        Map<String, Object> data = new HashMap<>();
        data.put("enabled", enabled);
        data.put("message", enabled ? "当前开放新用户注册" : "当前未开放新用户注册，请联系管理员");
        return R.ok(data);
    }

    private R<Map<String, Object>> adminLogin(String username, String password) {
        String configuredUsername = readParamValue("auth.admin.username");
        String configuredPassword = readParamValue("auth.admin.password");

        if (!username.equals(configuredUsername) || !password.equals(configuredPassword)) {
            return R.error("账号或密码错误");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("username", username);
        data.put("displayName", "管理员");
        data.put("loginType", "admin");
        return R.ok(data);
    }

    private R<Map<String, Object>> userLogin(String username, String password) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getIsActive, 1)
                .last("LIMIT 1");
        SysUser user = sysUserService.getOne(wrapper, false);
        if (user == null || !PasswordUtils.matches(password, user.getPasswordHash())) {
            return R.error("账号或密码错误");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("username", username);
        data.put("displayName", user.getDisplayName());
        data.put("loginType", "user");
        return R.ok(data);
    }

    private String readParamValue(String key) {
        LambdaQueryWrapper<SysParam> wrapper = new LambdaQueryWrapper<SysParam>()
                .eq(SysParam::getParamKey, key)
                .eq(SysParam::getIsActive, 1)
                .orderByDesc(SysParam::getId)
                .last("LIMIT 1");
        SysParam param = sysParamService.getOne(wrapper, false);
        return param == null || param.getParamValue() == null ? "" : param.getParamValue();
    }

    private boolean isUserRegisterEnabled() {
        String value = readParamValue("auth.user.register.enabled");
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        String normalized = value.trim();
        return "1".equals(normalized)
                || "true".equalsIgnoreCase(normalized)
                || "yes".equalsIgnoreCase(normalized)
                || "on".equalsIgnoreCase(normalized);
    }
}

package com.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.entity.SysUser;
import com.demo.service.SysUserService;
import com.demo.utils.PasswordUtils;
import com.demo.vo.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sys-user")
public class SysUserController {

    @Autowired
    private SysUserService sysUserService;

    @PostMapping("/page")
    public R<?> page(@RequestBody Map<String, Object> params) {
        long pageNum = Long.parseLong(String.valueOf(params.getOrDefault("pageNum", 1)));
        long pageSize = Long.parseLong(String.valueOf(params.getOrDefault("pageSize", 10)));
        String keyword = params.get("keyword") == null ? "" : String.valueOf(params.get("keyword")).trim();

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (!keyword.isEmpty()) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword).or().like(SysUser::getDisplayName, keyword));
        }
        wrapper.orderByDesc(SysUser::getCreatedAt);

        var page = sysUserService.page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize), wrapper);
        return R.ok(page);
    }

    @GetMapping("/{id}")
    public R<?> detail(@PathVariable Long id) {
        SysUser user = sysUserService.getById(id);
        if (user != null) {
            user.setPasswordHash(null);
        }
        return R.ok(user);
    }

    @PostMapping
    public R<?> create(@RequestBody SysUser user) {
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return R.error("账号不能为空");
        }
        if (user.getPasswordHash() == null || user.getPasswordHash().trim().isEmpty()) {
            return R.error("密码不能为空");
        }
        if (!user.getUsername().matches("[A-Za-z0-9_]{4,32}")) {
            return R.error("账号需为4到32位字母、数字或下划线");
        }
        LambdaQueryWrapper<SysUser> existWrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, user.getUsername().trim())
                .last("LIMIT 1");
        if (sysUserService.getOne(existWrapper, false) != null) {
            return R.error("账号已存在");
        }
        SysUser entity = new SysUser();
        entity.setUsername(user.getUsername().trim());
        entity.setPasswordHash(PasswordUtils.hash(user.getPasswordHash().trim()));
        entity.setDisplayName(user.getDisplayName() == null ? user.getUsername().trim() : user.getDisplayName().trim());
        entity.setIsActive(user.getIsActive() == null ? 1 : user.getIsActive());
        sysUserService.save(entity);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<?> update(@PathVariable Long id, @RequestBody SysUser user) {
        SysUser existing = sysUserService.getById(id);
        if (existing == null) {
            return R.error("用户不存在");
        }
        if (user.getDisplayName() != null) {
            existing.setDisplayName(user.getDisplayName().trim());
        }
        if (user.getIsActive() != null) {
            existing.setIsActive(user.getIsActive());
        }
        if (user.getPasswordHash() != null && !user.getPasswordHash().trim().isEmpty()) {
            existing.setPasswordHash(PasswordUtils.hash(user.getPasswordHash().trim()));
        }
        sysUserService.updateById(existing);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<?> delete(@PathVariable Long id) {
        sysUserService.removeById(id);
        return R.ok();
    }

    @PostMapping("/change-password")
    public R<?> changePassword(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String oldPassword = params.get("oldPassword");
        String newPassword = params.get("newPassword");
        String confirmPassword = params.get("confirmPassword");

        if (username == null || username.trim().isEmpty()) {
            return R.error("账号不能为空");
        }
        if (oldPassword == null || oldPassword.trim().isEmpty()) {
            return R.error("旧密码不能为空");
        }
        if (newPassword == null || newPassword.trim().isEmpty()) {
            return R.error("新密码不能为空");
        }
        if (!newPassword.equals(confirmPassword)) {
            return R.error("两次输入的新密码不一致");
        }
        if (newPassword.length() < 6 || newPassword.length() > 64) {
            return R.error("新密码长度需在6到64位之间");
        }

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username.trim())
                .last("LIMIT 1");
        SysUser user = sysUserService.getOne(wrapper, false);
        if (user == null) {
            return R.error("用户不存在");
        }
        if (!PasswordUtils.matches(oldPassword, user.getPasswordHash())) {
            return R.error("旧密码错误");
        }

        user.setPasswordHash(PasswordUtils.hash(newPassword));
        sysUserService.updateById(user);
        return R.ok();
    }

    @PostMapping("/reset-password")
    public R<?> resetPassword(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String newPassword = params.get("newPassword");
        String confirmPassword = params.get("confirmPassword");

        if (username == null || username.trim().isEmpty()) {
            return R.error("账号不能为空");
        }
        if (newPassword == null || newPassword.trim().isEmpty()) {
            return R.error("新密码不能为空");
        }
        if (!newPassword.equals(confirmPassword)) {
            return R.error("两次输入的新密码不一致");
        }
        if (newPassword.length() < 6 || newPassword.length() > 64) {
            return R.error("新密码长度需在6到64位之间");
        }

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username.trim())
                .last("LIMIT 1");
        SysUser user = sysUserService.getOne(wrapper, false);
        if (user == null) {
            return R.error("用户不存在");
        }

        user.setPasswordHash(PasswordUtils.hash(newPassword));
        sysUserService.updateById(user);
        return R.ok();
    }
}
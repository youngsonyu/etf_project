package com.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.entity.MenuConfig;
import com.demo.mapper.MenuConfigMapper;
import com.demo.service.MenuConfigService;
import org.springframework.stereotype.Service;

@Service
public class MenuConfigServiceImpl extends ServiceImpl<MenuConfigMapper, MenuConfig> implements MenuConfigService {
}
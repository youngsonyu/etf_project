package com.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.entity.SysParam;
import com.demo.mapper.SysParamMapper;
import com.demo.service.SysParamService;
import org.springframework.stereotype.Service;

@Service
public class SysParamServiceImpl extends ServiceImpl<SysParamMapper, SysParam> implements SysParamService {
}

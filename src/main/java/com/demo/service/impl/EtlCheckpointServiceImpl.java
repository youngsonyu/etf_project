package com.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.entity.EtlCheckpoint;
import com.demo.mapper.EtlCheckpointMapper;
import com.demo.service.EtlCheckpointService;
import org.springframework.stereotype.Service;

@Service
public class EtlCheckpointServiceImpl extends ServiceImpl<EtlCheckpointMapper, EtlCheckpoint> implements EtlCheckpointService {
}

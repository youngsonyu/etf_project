package com.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.entity.EtlBatchStatus;
import com.demo.mapper.EtlBatchStatusMapper;
import com.demo.service.EtlBatchStatusService;
import org.springframework.stereotype.Service;

@Service
public class EtlBatchStatusServiceImpl extends ServiceImpl<EtlBatchStatusMapper, EtlBatchStatus> implements EtlBatchStatusService {
}

package com.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.entity.TradeCalendar;
import com.demo.mapper.TradeCalendarMapper;
import com.demo.service.TradeCalendarService;
import org.springframework.stereotype.Service;

@Service
public class TradeCalendarServiceImpl extends ServiceImpl<TradeCalendarMapper, TradeCalendar> implements TradeCalendarService {
}

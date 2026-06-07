package com.demo.service;

import com.demo.entity.EtfFiveDimensionResonance;
import com.demo.vo.PageParam;
import com.demo.vo.PageResult;

import java.util.List;
import java.util.Map;

public interface EtfFiveDimensionResonanceService extends BaseCrudService<EtfFiveDimensionResonance> {
    PageResult<EtfFiveDimensionResonance> pageByCondition(PageParam param);

    List<EtfFiveDimensionResonance> listForExport(PageParam param);

    String getLatestTradeDate();

    String getLastTriggeredDate();

    Map<String, Object> refreshLatestTradeDate();
}

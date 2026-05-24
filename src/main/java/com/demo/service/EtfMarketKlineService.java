package com.demo.service;

import com.demo.entity.EtfMarketKline;
import com.demo.vo.PageParam;
import com.demo.vo.PageResult;

public interface EtfMarketKlineService extends BaseCrudService<EtfMarketKline> {
	PageResult<com.demo.vo.EtfMarketKlineWithNameVO> pageWithName(PageParam param);
}

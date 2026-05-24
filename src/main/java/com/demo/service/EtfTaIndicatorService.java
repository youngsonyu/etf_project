package com.demo.service;

import com.demo.entity.EtfTaIndicator;
import com.demo.vo.PageParam;
import com.demo.vo.PageResult;

public interface EtfTaIndicatorService extends BaseCrudService<EtfTaIndicator> {
	PageResult<EtfTaIndicator> pageSafe(PageParam param);
}

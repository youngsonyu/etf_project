package com.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.entity.EtfMarketKline;
import com.demo.mapper.EtfMarketKlineMapper;
import com.demo.service.EtfMarketKlineService;
import org.springframework.stereotype.Service;

@Service
public class EtfMarketKlineServiceImpl extends ServiceImpl<EtfMarketKlineMapper, EtfMarketKline> implements EtfMarketKlineService {
	@Override
	public com.demo.vo.PageResult<com.demo.vo.EtfMarketKlineWithNameVO> pageWithName(com.demo.vo.PageParam param) {
		int offset = (param.getPageNum() - 1) * param.getPageSize();
		int size = param.getPageSize();
		java.util.Map<String, Object> filters = param.getFilters() == null ? java.util.Collections.emptyMap() : param.getFilters();
		String etfCode = filters.getOrDefault("etfCode", "").toString();
		String etfName = filters.getOrDefault("etfName", "").toString();
		String period = filters.getOrDefault("period", "").toString();
		String tradeTimeDate = filters.getOrDefault("tradeTimeDate", "").toString();
		String sortField = filters.getOrDefault("sortField", "").toString();
		String sortOrder = filters.getOrDefault("sortOrder", "").toString();

		long total = baseMapper.countWithEtfName(etfCode, etfName, period, tradeTimeDate);
		java.util.List<com.demo.vo.EtfMarketKlineWithNameVO> records = baseMapper.pageWithEtfName(offset, size, etfCode, etfName, period, tradeTimeDate, sortField, sortOrder);
		com.demo.vo.PageResult<com.demo.vo.EtfMarketKlineWithNameVO> result = new com.demo.vo.PageResult<>();
		result.setTotal(total);
		result.setRecords(records);
		result.setCurrent(param.getPageNum());
		result.setSize(param.getPageSize());
		return result;
	}
}

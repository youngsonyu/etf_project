package com.demo.service.impl;

import com.demo.vo.PageParam;
import com.demo.vo.PageResult;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.entity.EtfTaIndicator;
import com.demo.mapper.EtfTaIndicatorMapper;
import com.demo.service.EtfTaIndicatorService;
import org.springframework.stereotype.Service;

@Service
public class EtfTaIndicatorServiceImpl extends ServiceImpl<EtfTaIndicatorMapper, EtfTaIndicator> implements EtfTaIndicatorService {
	@Override
	public PageResult<EtfTaIndicator> pageSafe(PageParam param) {
		int offset = (param.getPageNum() - 1) * param.getPageSize();
		int size = param.getPageSize();

		java.util.Map<String, Object> filters = param.getFilters() == null ? java.util.Collections.emptyMap() : param.getFilters();
		String keyword = param.getKeyword();
		String etfName = filters.getOrDefault("etfName", "").toString();
		String period = filters.getOrDefault("period", "").toString();
		String tradeTimeDate = filters.getOrDefault("tradeTimeDate", "").toString();
		String source = filters.getOrDefault("source", "").toString();
		Integer signalTrendLong = parseInteger(filters.get("signalTrendLong"));
		Integer isMacdRed = parseInteger(filters.get("isMacdRed"));
		Integer macdHistDirection = parseInteger(filters.get("macdHistDirection"));
		Integer isSarBullish = parseInteger(filters.get("isSarBullish"));
		Integer sarTrend = parseInteger(filters.get("sarTrend"));
		Integer signalMomentumLong = parseInteger(filters.get("signalMomentumLong"));
		Integer signalWarning = parseInteger(filters.get("signalWarning"));
		String sortField = filters.getOrDefault("sortField", "").toString();
		String sortOrder = filters.getOrDefault("sortOrder", "").toString();

		long total = baseMapper.countSafe(keyword, etfName, period, tradeTimeDate, source, signalTrendLong,
				isMacdRed, macdHistDirection, isSarBullish, sarTrend, signalMomentumLong, signalWarning);
		java.util.List<EtfTaIndicator> records = baseMapper.pageSafe(offset, size, keyword, etfName, period,
				tradeTimeDate, source, signalTrendLong, isMacdRed, macdHistDirection, isSarBullish, sarTrend,
				signalMomentumLong, signalWarning, sortField, sortOrder);

		PageResult<EtfTaIndicator> result = new PageResult<>();
		result.setTotal(total);
		result.setRecords(records);
		result.setCurrent(param.getPageNum());
		result.setSize(param.getPageSize());
		return result;
	}

	private Integer parseInteger(Object value) {
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value).trim();
		if (text.isEmpty()) {
			return null;
		}
		try {
			return Integer.valueOf(text);
		} catch (NumberFormatException ex) {
			return null;
		}
	}
}

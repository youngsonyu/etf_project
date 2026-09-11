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
		// period 可能是字符串或字符串数组（多选下拉框），统一转成 List<String> 给 mapper
		java.util.List<String> period = toStringList(filters.get("period"));
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

	/**
	 * 把任意类型的 period 值（String / List / null）统一转成 List<String>。
	 * 用于多选下拉框场景：axios 把数组序列化为 ?period=day&period=week，
	 * Spring 反序列化为 List<String>；老逻辑前端用逗号分隔字符串时是 String。
	 */
	private java.util.List<String> toStringList(Object value) {
		java.util.List<String> result = new java.util.ArrayList<>();
		if (value == null) {
			return result;
		}
		if (value instanceof java.util.Collection) {
			for (Object o : (java.util.Collection<?>) value) {
				if (o != null) {
					String s = String.valueOf(o).trim();
					if (!s.isEmpty()) {
						result.add(s);
					}
				}
			}
			return result;
		}
		// 兼容旧逻辑：逗号分隔字符串
		String text = String.valueOf(value).trim();
		if (text.isEmpty()) {
			return result;
		}
		for (String s : text.split(",")) {
			String t = s.trim();
			if (!t.isEmpty()) {
				result.add(t);
			}
		}
		return result;
	}
}

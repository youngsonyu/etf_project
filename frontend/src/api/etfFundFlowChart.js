import request from '@/utils/request'

export default {
  chartData(data) {
    return request({
      url: '/api/etf_fund_flow_summary/chart-data',
      method: 'post',
      data
    })
  },
  flowComparison(data) {
    return request({
      url: '/api/etf_fund_flow_summary/flow-comparison',
      method: 'post',
      data
    })
  },
  getFlowDataByDate(tradeDate) {
    return request({
      url: '/api/etf_fund_flow_summary/chart-flow-data',
      method: 'get',
      params: { tradeDate }
    })
  }
}
import { createCrudApi } from './crudFactory'
import request from '@/utils/request'

const api = createCrudApi('etf_fund_flow_summary')

api.accumulate = (data) => request({
	url: '/api/etf_fund_flow_summary/accumulate',
	method: 'post',
	data
})

api.chartData = (data) => request({
	url: '/api/etf_fund_flow_summary/chart-data',
	method: 'post',
	data
})

export default api

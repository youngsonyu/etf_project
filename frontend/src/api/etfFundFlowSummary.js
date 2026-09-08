import { createCrudApi } from './crudFactory'
import request, { LONG_TASK_TIMEOUT } from '@/utils/request'

const api = createCrudApi('etf_fund_flow_summary')

api.accumulate = (data) => request({
	url: '/api/etf_fund_flow_summary/accumulate',
	method: 'post',
	data,
	timeout: LONG_TASK_TIMEOUT
})

api.chartData = (data) => request({
	url: '/api/etf_fund_flow_summary/chart-data',
	method: 'post',
	data
})

export default api

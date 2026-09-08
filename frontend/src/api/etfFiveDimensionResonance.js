import { createCrudApi } from './crudFactory'
import request from '@/utils/request'

const api = createCrudApi('etf_five_dimension_resonance')

api.exportData = (data = {}) => request({
	url: '/api/etf_five_dimension_resonance/export-data',
	method: 'post',
	data
})

api.latestTradeDate = () => request({
	url: '/api/etf_five_dimension_resonance/latest-trade-date',
	method: 'get'
})

api.lastTriggeredDate = () => request({
	url: '/api/etf_five_dimension_resonance/last-triggered-date',
	method: 'get'
})

api.refreshLatest = () => request({
	url: '/api/etf_five_dimension_resonance/refresh-latest',
	method: 'post'
})

api.backfill = (data) => request({
	url: '/api/etf_five_dimension_resonance/backfill',
	method: 'post',
	data
})

api.backfillAll = () => request({
	url: '/api/etf_five_dimension_resonance/backfill-all',
	method: 'post'
})

api.taskStatus = (taskId) => request({
	url: `/api/etf_five_dimension_resonance/tasks/${taskId}`,
	method: 'get'
})

export default api

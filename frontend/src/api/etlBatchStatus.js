import { createCrudApi } from './crudFactory'
import request, { LONG_TASK_TIMEOUT } from '@/utils/request'

const api = createCrudApi('etl_batch_status')

api.importGalaxy = () => request({
	url: '/api/etl_batch_status/import-galaxy',
	method: 'post',
	timeout: LONG_TASK_TIMEOUT
})

api.calcTa = () => request({
	url: '/api/etl_batch_status/calc-ta',
	method: 'post',
	timeout: LONG_TASK_TIMEOUT
})

api.calcTaDaily = () => request({
	url: '/api/etl_batch_status/calc-ta-daily',
	method: 'post',
	timeout: LONG_TASK_TIMEOUT
})

export default api

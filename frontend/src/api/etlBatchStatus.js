import { createCrudApi } from './crudFactory'
import request from '@/utils/request'

const api = createCrudApi('etl_batch_status')

api.importGalaxy = () => request({
	url: '/api/etl_batch_status/import-galaxy',
	method: 'post',
	timeout: 180000
})

export default api

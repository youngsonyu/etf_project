import { createCrudApi } from './crudFactory'
import request, { LONG_TASK_TIMEOUT } from '../utils/request'

const api = createCrudApi('etf_five_dimension_report')

api.latest = (size = 5) => request({
  url: '/api/etf_five_dimension_report/latest',
  method: 'get',
  params: { size }
})

api.generateLatest = (publisher) => request({
  url: '/api/etf_five_dimension_report/generate-latest',
  method: 'post',
  params: { publisher },
  timeout: LONG_TASK_TIMEOUT
})

export default api

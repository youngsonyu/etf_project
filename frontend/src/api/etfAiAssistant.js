import request, { LONG_TASK_TIMEOUT } from '../utils/request'

export default {
  chat(data) {
    return request({
      url: '/api/ai/chat',
      method: 'post',
      data,
      timeout: LONG_TASK_TIMEOUT
    })
  }
}

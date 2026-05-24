import request from '../utils/request'

export default {
  chat(data) {
    return request({
      url: '/api/ai/chat',
      method: 'post',
      data,
      timeout: 180000 // AI 响应最长 3 分钟
    })
  }
}

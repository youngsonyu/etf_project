import request from '../utils/request'

export default {
  login(data) {
    return request({
      url: '/api/auth/login',
      method: 'post',
      data
    })
  },
  registerEnabled() {
    return request({
      url: '/api/auth/register-enabled',
      method: 'get'
    })
  },
  register(data) {
    return request({
      url: '/api/auth/register',
      method: 'post',
      data
    })
  }
}

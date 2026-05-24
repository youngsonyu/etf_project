import request from '../utils/request'

export default {
  visible() {
    return request({
      url: '/api/menu_config/visible',
      method: 'get'
    })
  }
}
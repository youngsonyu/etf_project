import request from '../utils/request'

export default {
  currentTradeDate() {
    return request({
      url: '/api/etl_progress/current-trade-date',
      method: 'get'
    })
  }
}

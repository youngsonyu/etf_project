import request from '../utils/request'

export default {
  page: (params) => request({
    url: '/api/sys-user/page',
    method: 'post',
    data: params
  }),
  detail: (id) => request({
    url: `/api/sys-user/${id}`,
    method: 'get'
  }),
  add: (data) => request({
    url: '/api/sys-user',
    method: 'post',
    data
  }),
  update: (id, data) => request({
    url: `/api/sys-user/${id}`,
    method: 'put',
    data
  }),
  remove: (id) => request({
    url: `/api/sys-user/${id}`,
    method: 'delete'
  }),
  changePassword: (data) => request({
    url: '/api/sys-user/change-password',
    method: 'post',
    data
  }),
  resetPassword: (data) => request({
    url: '/api/sys-user/reset-password',
    method: 'post',
    data
  })
}
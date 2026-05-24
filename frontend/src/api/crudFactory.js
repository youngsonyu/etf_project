import request from '../utils/request'

export function createCrudApi(moduleName) {
  const base = `/api/${moduleName}`
  return {
    page(data) {
      return request({
        url: `${base}/page`,
        method: 'post',
        data
      })
    },
    detail(id) {
      return request({
        url: `${base}/${id}`,
        method: 'get'
      })
    },
    add(data) {
      return request({
        url: `${base}`,
        method: 'post',
        data
      })
    },
    update(id, data) {
      return request({
        url: `${base}/${id}`,
        method: 'put',
        data
      })
    },
    remove(id) {
      return request({
        url: `${base}/${id}`,
        method: 'delete'
      })
    },
    batchDelete(ids) {
      return request({
        url: `${base}/batch`,
        method: 'delete',
        data: ids
      })
    }
  }
}

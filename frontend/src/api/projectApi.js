import client from './client.js';

export const projectApi = {
  create: (data) => client.post('/projects', data),
  get: (id) => client.get(`/projects/${id}`),
  list: (params) => client.get('/projects', { params }),
  delete: (id) => client.delete(`/projects/${id}`),
};

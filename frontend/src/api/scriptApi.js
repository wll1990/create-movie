import client from './client.js';

export const scriptApi = {
  generate: (projectId, data) =>
    client.post(`/projects/${projectId}/scripts`, data),
  get: (projectId) =>
    client.get(`/projects/${projectId}/scripts`),
  update: (projectId, data) =>
    client.put(`/projects/${projectId}/scripts`, data),
};

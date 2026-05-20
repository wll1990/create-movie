import client from './client.js';

export const episodeApi = {
  list: (projectId) =>
    client.get(`/projects/${projectId}/episodes`),

  create: (projectId, data) =>
    client.post(`/projects/${projectId}/episodes`, data),

  get: (episodeId) =>
    client.get(`/episodes/${episodeId}`),

  createNext: (projectId) =>
    client.post(`/projects/${projectId}/episodes/next`),
};

import client from './client.js';

export const analysisApi = {
  analyze: (projectId, formData) =>
    client.post(`/projects/${projectId}/analyze`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }),
  getGene: (projectId) =>
    client.get(`/projects/${projectId}/gene`),
  createTemplate: (geneId, data) =>
    client.post(`/genes/${geneId}/template`, data),
  listTemplates: () =>
    client.get('/templates'),
};

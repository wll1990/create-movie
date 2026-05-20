import client from './client.js';

export const characterApi = {
  generate: (projectId) =>
    client.post(`/projects/${projectId}/characters`),
  get: (projectId) =>
    client.get(`/projects/${projectId}/characters`),

  // Gacha-style async image generation
  getCandidates: (projectId, charId) =>
    client.get(`/projects/${projectId}/characters/${charId}/candidates`),
  selectPortrait: (projectId, charId, candidateIndex) =>
    client.put(`/projects/${projectId}/characters/${charId}/select-portrait`, { candidateIndex }),
  getImageStatus: (projectId, charId) =>
    client.get(`/projects/${projectId}/characters/${charId}/image-status`),

  // Three-view
  getThreeViewStatus: (projectId, charId) =>
    client.get(`/projects/${projectId}/characters/${charId}/threeview`),
  regenerateThreeView: (projectId, charId) =>
    client.post(`/projects/${projectId}/characters/${charId}/threeview/regenerate`),

  // Expression candidates (gacha-style per emotion)
  getExpressionCandidates: (projectId, charId) =>
    client.get(`/projects/${projectId}/characters/${charId}/expressions/candidates`),
  selectExpression: (projectId, charId, emotionType, candidateIndex) =>
    client.put(`/projects/${projectId}/characters/${charId}/expressions/select`,
      { emotionType, candidateIndex }),
  regenerateExpressions: (projectId, charId) =>
    client.post(`/projects/${projectId}/characters/${charId}/expressions/regenerate`),
};

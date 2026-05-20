import client from './client.js';

export const storyboardApi = {
  generate: (projectId) =>
    client.post(`/projects/${projectId}/storyboards`),
  get: (projectId) =>
    client.get(`/projects/${projectId}/storyboards`),

  // Frame editing
  updateFrame: (projectId, frameId, updates) =>
    client.put(`/projects/${projectId}/storyboards/frames/${frameId}`, updates),

  // Regeneration
  regenerateAll: (projectId) =>
    client.post(`/projects/${projectId}/storyboards/regenerate`),
  regenerateFrame: (projectId, frameId, customPrompt) =>
    client.post(`/projects/${projectId}/storyboards/frames/${frameId}/regenerate`,
      customPrompt ? { customPrompt } : {}),

  // Prompt viewing
  getStoryboardPrompt: (projectId) =>
    client.get(`/projects/${projectId}/storyboards/prompt`),
  getFramePrompt: (projectId, frameId) =>
    client.get(`/projects/${projectId}/storyboards/frames/${frameId}/prompt`),
};

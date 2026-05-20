import client from './client.js';

export const compositionApi = {
  submit: (projectId) =>
    client.post(`/projects/${projectId}/compositions`),
  get: (projectId) =>
    client.get(`/projects/${projectId}/compositions`),
  progress: (projectId, compId) =>
    client.get(`/projects/${projectId}/compositions/${compId}/progress`),
  setBgm: (projectId, compId, bgmMaterialId) =>
    client.patch(`/projects/${projectId}/compositions/${compId}`, { bgmMaterialId }),
  getDownloadUrl: (projectId, compId) =>
    `/api/projects/${projectId}/compositions/${compId}/download`,

  // V2: Voice generation
  generateVoice: (projectId) =>
    client.post(`/projects/${projectId}/voice`),

  // V2: Clip generation (frame-by-frame)
  initClips: (projectId) =>
    client.post(`/projects/${projectId}/clips/start`),
  getClipPrerequisites: (projectId) =>
    client.get(`/projects/${projectId}/clips/prerequisites`),
  getClipProgress: (projectId) =>
    client.get(`/projects/${projectId}/clips/progress`),
  getCurrentFrame: (projectId) =>
    client.get(`/projects/${projectId}/clips/current`),
  updateFramePrompt: (projectId, frameId, prompt) =>
    client.put(`/projects/${projectId}/clips/frames/${frameId}/prompt`, { prompt }),
  generateFrameClip: (projectId, frameId) =>
    client.post(`/projects/${projectId}/clips/frames/${frameId}/generate`),
  approveFrame: (projectId, frameId) =>
    client.post(`/projects/${projectId}/clips/frames/${frameId}/approve`),
  skipFrame: (projectId, frameId) =>
    client.post(`/projects/${projectId}/clips/frames/${frameId}/skip`),
  retryFrame: (projectId, frameId) =>
    client.post(`/projects/${projectId}/clips/frames/${frameId}/retry`),
  getFrameDetail: (projectId, frameId) =>
    client.get(`/projects/${projectId}/clips/frames/${frameId}/detail`),
};

// BGM materials API
export const materialApi = {
  listBgm: () => client.get('/materials/bgm'),
};

// TTS voices API
export const ttsApi = {
  listVoices: () => client.get('/tts/voices'),
  previewVoice: (projectId, params) =>
    client.post(`/projects/${projectId}/voice/preview`, params, { responseType: 'blob' }),
  getVoicePrompt: (projectId) =>
    client.get(`/projects/${projectId}/voice/prompt`),
};

// Character voice config API
export const characterApi2 = {
  updateVoice: (projectId, charId, voiceConfig) =>
    client.put(`/projects/${projectId}/characters/${charId}/voice`, voiceConfig),
};

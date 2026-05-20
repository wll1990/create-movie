import client from './client.js';

export const workflowApi = {
  getLogs: (projectId) =>
    client.get(`/workflow/logs/${projectId}`),
  getStepLog: (projectId, step) =>
    client.get(`/workflow/logs/${projectId}/${step}`),
};

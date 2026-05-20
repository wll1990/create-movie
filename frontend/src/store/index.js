import { configureStore } from '@reduxjs/toolkit';
import projectReducer from './projectSlice.js';
import workflowReducer from './workflowSlice.js';

export const store = configureStore({
  reducer: {
    project: projectReducer,
    workflow: workflowReducer,
  },
});

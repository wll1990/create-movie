import { createSlice } from '@reduxjs/toolkit';

const workflowSlice = createSlice({
  name: 'workflow',
  initialState: {
    logs: [],
    stepDetail: null,
    loading: false,
  },
  reducers: {
    setLogs(state, action) {
      state.logs = action.payload;
    },
    setStepDetail(state, action) {
      state.stepDetail = action.payload;
    },
    setLoading(state, action) {
      state.loading = action.payload;
    },
    clearStepDetail(state) {
      state.stepDetail = null;
    },
  },
});

export const { setLogs, setStepDetail, setLoading, clearStepDetail } =
  workflowSlice.actions;
export default workflowSlice.reducer;

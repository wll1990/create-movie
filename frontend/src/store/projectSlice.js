import { createSlice } from '@reduxjs/toolkit';

const projectSlice = createSlice({
  name: 'project',
  initialState: {
    current: null,
    list: [],
    loading: false,
    error: null,
  },
  reducers: {
    setLoading(state, action) {
      state.loading = action.payload;
    },
    setCurrent(state, action) {
      state.current = action.payload;
    },
    setList(state, action) {
      state.list = action.payload;
    },
    updateProgress(state, action) {
      if (state.current) {
        state.current.progress = action.payload;
      }
    },
    setError(state, action) {
      state.error = action.payload;
    },
    clearCurrent(state) {
      state.current = null;
    },
  },
});

export const { setLoading, setCurrent, setList, updateProgress, setError, clearCurrent } =
  projectSlice.actions;
export default projectSlice.reducer;

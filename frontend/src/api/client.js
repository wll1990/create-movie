import axios from 'axios';

const client = axios.create({
  baseURL: '/api',
  timeout: 120000,
  headers: { 'Content-Type': 'application/json' },
});

// Response interceptor for error handling
client.interceptors.response.use(
  (resp) => resp.data,
  (err) => {
    const msg = err.response?.data?.message || err.message || '请求失败';
    console.error('API Error:', msg);
    return Promise.reject(err);
  }
);

export default client;

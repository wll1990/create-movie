import { Routes, Route } from 'react-router-dom';
import Layout from './components/Layout/Layout.jsx';
import HomePage from './pages/HomePage.jsx';
import CreationPage from './pages/CreationPage.jsx';
import ProjectPage from './pages/ProjectPage.jsx';

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/project/:projectId" element={<ProjectPage />} />
        <Route path="/create" element={<CreationPage />} />
      </Route>
    </Routes>
  );
}

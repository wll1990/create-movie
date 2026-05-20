import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { PlusCircle, Film, Play, Search } from 'lucide-react';
import { projectApi } from '../api/projectApi.js';

const MODE_LABELS = {
  ANALYSIS: '爆款分析',
  CREATION: '从零创作',
  HYBRID: '混合模式',
};

export default function HomePage() {
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadProjects();
  }, []);

  const loadProjects = async () => {
    try {
      const data = await projectApi.list();
      setProjects(data?.content || data || []);
    } catch (e) {
      console.error('Failed to load projects:', e);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id) => {
    if (!confirm('确定删除此项目？')) return;
    try {
      await projectApi.delete(id);
      setProjects(projects.filter((p) => p.id !== id));
    } catch (e) {
      alert('删除失败: ' + e.message);
    }
  };

  return (
    <div>
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold">项目列表</h1>
          <p className="text-gray-500 text-sm mt-1">管理你的漫剧创作项目</p>
        </div>
        <Link
          to="/create"
          className="inline-flex items-center gap-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-700 rounded-lg text-sm font-medium transition-colors"
        >
          <PlusCircle className="w-4 h-4" />
          新建项目
        </Link>
      </div>

      {/* Project Grid */}
      {loading ? (
        <div className="text-center py-20 text-gray-500">加载中...</div>
      ) : projects.length === 0 ? (
        <div className="text-center py-16">
          <Film className="w-16 h-16 text-gray-700 mx-auto mb-6" />
          <h2 className="text-xl font-semibold text-white mb-2">欢迎使用 MakeMovie</h2>
          <p className="text-gray-400 mb-2">专业的 AI 漫剧创作平台，三步产出红果剧场级视频</p>
          <div className="inline-flex items-center gap-6 text-xs text-gray-500 mb-8">
            <span>① 输入主题</span>
            <span className="text-gray-700">→</span>
            <span>② 逐帧审核</span>
            <span className="text-gray-700">→</span>
            <span>③ 导出成品</span>
          </div>
          <div>
            <Link
              to="/create"
              className="inline-flex items-center gap-2 px-6 py-3 bg-blue-600 hover:bg-blue-700 rounded-xl font-medium transition-colors"
            >
              <PlusCircle className="w-5 h-5" />
              创建第一个项目
            </Link>
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {projects.map((project) => (
            <div key={project.id}
                 className="bg-gray-900 border border-gray-800 hover:border-blue-700 rounded-xl p-4 transition-colors group">
              <div className="flex items-start justify-between mb-3">
                <div>
                  <h3 className="font-semibold text-white group-hover:text-blue-400 transition-colors">
                    {project.title}
                  </h3>
                  <p className="text-xs text-gray-500 mt-0.5">
                    {project.track || '未指定赛道'} · {MODE_LABELS[project.mode] || project.mode}
                    {project.episodeCount ? ` · ${project.episodeCount}集` : ''}
                  </p>
                </div>
                <span className={`text-xs px-2 py-0.5 rounded ${
                  project.status === 'COMPLETED' ? 'bg-green-900/50 text-green-400' :
                  project.status === 'PROCESSING' ? 'bg-blue-900/50 text-blue-400' :
                  'bg-gray-700 text-gray-400'
                }`}>
                  {project.status}
                </span>
              </div>

              {/* Progress bar */}
              {project.progress && (
                <div className="mb-3">
                  <div className="w-full h-1 bg-gray-800 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-blue-500 rounded-full transition-all"
                      style={{ width: `${project.progress.overallProgress || 0}%` }}
                    />
                  </div>
                  <p className="text-xs text-gray-600 mt-1">
                    {project.progress.completedSteps || 0}/{project.progress.totalSteps || 6} 步骤完成
                  </p>
                </div>
              )}

              {/* Actions */}
              <div className="flex items-center gap-2 pt-3 border-t border-gray-800">
                <Link
                  to={`/project/${project.id}`}
                  className="flex-1 inline-flex items-center justify-center gap-1 px-3 py-1.5 text-xs bg-gray-800 hover:bg-gray-700 rounded-lg transition-colors"
                >
                  <Play className="w-3 h-3" />
                  打开项目
                </Link>
                <button
                  onClick={() => handleDelete(project.id)}
                  className="px-3 py-1.5 text-xs text-red-400 hover:bg-red-900/20 rounded-lg transition-colors"
                >
                  删除
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

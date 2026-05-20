import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Sparkles, Upload, Search, FileText } from 'lucide-react';
import { projectApi } from '../api/projectApi.js';

const TRACKS = [
  '都市甜宠', '都市职场', '悬疑反转', '古装仙侠', '现代言情',
  '轻喜剧', '科幻奇幻', '现实题材', '青春校园', '历史权谋',
];
const MODES = [
  { value: 'CREATION', label: '从零创作', desc: '直接输入主题生成剧本', icon: FileText },
  { value: 'ANALYSIS', label: '爆款分析', desc: '上传视频提取基因', icon: Search },
  { value: 'HYBRID', label: '混合模式', desc: '先分析再创作', icon: Sparkles },
];

export default function CreationPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [mode, setMode] = useState('CREATION');
  const [title, setTitle] = useState('');
  const [track, setTrack] = useState('都市甜宠');
  const [theme, setTheme] = useState('');
  const [uploadedFile, setUploadedFile] = useState(null);

  const handleCreate = async () => {
    if (!title.trim()) return;

    try {
      const project = await projectApi.create({
        title,
        track,
        mode,
        theme: theme.trim() || null,
      });
      navigate(`/project/${project.id}`);
    } catch (e) {
      alert('创建失败: ' + e.response?.data?.message || e.message);
    }
  };

  return (
    <div className="max-w-2xl mx-auto">
      <h1 className="text-2xl font-bold mb-2">新建项目</h1>
      <p className="text-sm text-gray-500 mb-6">
        创建一个漫剧项目，AI 会从剧本到成片自动完成 8 个步骤。你只需在关键节点审核和调整。
      </p>

      {/* Step 1: Mode selection */}
      <div className="mb-8">
        <h2 className="text-sm font-semibold text-gray-400 uppercase tracking-wider mb-3">
          1. 选择模式
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          {MODES.map(({ value, label, desc, icon: Icon }) => (
            <button
              key={value}
              onClick={() => setMode(value)}
              className={`flex flex-col items-center gap-2 p-4 rounded-xl border-2 transition-all text-left ${
                mode === value
                  ? 'border-blue-500 bg-blue-900/20'
                  : 'border-gray-700 bg-gray-900 hover:border-gray-600'
              }`}
            >
              <Icon className={`w-6 h-6 ${mode === value ? 'text-blue-400' : 'text-gray-500'}`} />
              <span className="font-medium text-sm">{label}</span>
              <span className="text-xs text-gray-500 text-center">{desc}</span>
            </button>
          ))}
        </div>
        <p className="text-xs text-gray-600 mt-2">
          💡 推荐新手选择"从零创作"，只需输入主题即可体验全流程。
        </p>
      </div>

      {/* Step 2: Project info */}
      <div className="mb-8">
        <h2 className="text-sm font-semibold text-gray-400 uppercase tracking-wider mb-3">
          2. 项目信息
        </h2>
        <div className="space-y-4">
          <div>
            <label className="block text-sm text-gray-400 mb-1">项目名称</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="给你的项目起个名字..."
              className="w-full bg-gray-800 border border-gray-700 rounded-lg px-4 py-2.5 text-white placeholder-gray-500 focus:outline-none focus:border-blue-500"
            />
            <p className="text-xs text-gray-600 mt-1">用于识别项目，不影响生成内容。</p>
          </div>

          <div>
            <label className="block text-sm text-gray-400 mb-1">赛道</label>
            <div className="grid grid-cols-5 gap-2">
              {TRACKS.map((t) => (
                <button
                  key={t}
                  onClick={() => setTrack(t)}
                  className={`px-3 py-2 rounded-lg text-sm transition-colors ${
                    track === t
                      ? 'bg-blue-600 text-white'
                      : 'bg-gray-800 text-gray-400 hover:bg-gray-700'
                  }`}
                >
                  {t}
                </button>
              ))}
            </div>
            <p className="text-xs text-gray-600 mt-1">
              决定剧本风格、角色造型和画面调性。不同类型会有不同的叙事套路。
            </p>
          </div>

          {mode !== 'ANALYSIS' && (
            <div>
              <label className="block text-sm text-gray-400 mb-1">创作主题</label>
              <input
                type="text"
                value={theme}
                onChange={(e) => setTheme(e.target.value)}
                placeholder="如：霸道总裁爱上咖啡店打工女孩..."
                className="w-full bg-gray-800 border border-gray-700 rounded-lg px-4 py-2.5 text-white placeholder-gray-500 focus:outline-none focus:border-blue-500"
              />
              <p className="text-xs text-gray-600 mt-1">
                一句话描述你想讲的故事。越具体，AI 生成的剧本越贴合你的预期。
              </p>
            </div>
          )}

          {mode === 'ANALYSIS' && (
            <div>
              <label className="block text-sm text-gray-400 mb-1">上传爆款视频</label>
              <div className="border-2 border-dashed border-gray-700 rounded-lg p-8 text-center hover:border-blue-500 transition-colors">
                <Upload className="w-8 h-8 text-gray-500 mx-auto mb-2" />
                <p className="text-sm text-gray-500">拖放或点击上传视频文件</p>
                <input
                  type="file"
                  accept="video/*"
                  onChange={(e) => setUploadedFile(e.target.files[0])}
                  className="mt-3 text-sm text-gray-400 file:mr-3 file:py-1.5 file:px-4 file:rounded-lg file:bg-blue-600 file:text-white file:border-0"
                />
              </div>
              <p className="text-xs text-gray-600 mt-1">
                上传一个你喜欢的爆款漫剧参考视频，系统会分析其内容/视觉/音频/流量基因。
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Create button */}
      <button
        onClick={handleCreate}
        disabled={!title.trim()}
        className="w-full py-3 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl font-medium transition-colors"
      >
        创建项目
      </button>

      <p className="text-center text-xs text-gray-600 mt-4">
        创建后会自动跳转到项目工作台，你可以在左侧"快捷操作"面板逐步推进创作流程。
      </p>
    </div>
  );
}

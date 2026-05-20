import { useState, useEffect } from 'react';
import { ChevronDown, PlusCircle } from 'lucide-react';
import { episodeApi } from '../../api/episodeApi.js';

export default function EpisodeSelector({ projectId, currentEpisodeId, onEpisodeChange }) {
  const [episodes, setEpisodes] = useState([]);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    loadEpisodes();
  }, [projectId]);

  const loadEpisodes = async () => {
    try {
      const data = await episodeApi.list(projectId);
      setEpisodes(data || []);
    } catch (e) {
      console.error('Failed to load episodes:', e);
    }
  };

  const currentEpisode = episodes.find(e => e.id === currentEpisodeId) || episodes[0];

  const handleCreateNext = async () => {
    try {
      const ep = await episodeApi.createNext(projectId);
      await loadEpisodes();
      onEpisodeChange(ep.id);
      setOpen(false);
    } catch (e) {
      alert('创建新集失败: ' + (e.response?.data?.message || e.message));
    }
  };

  return (
    <div className="relative">
      <button
        onClick={() => setOpen(!open)}
        className="flex items-center gap-2 px-4 py-2 bg-gray-900 border border-gray-700 rounded-lg
                   hover:border-gray-600 transition-colors text-sm"
      >
        <span className="text-gray-400">
          第{currentEpisode?.episodeNumber || 1}集
        </span>
        <span className="text-white font-medium max-w-[120px] truncate">
          {currentEpisode?.title || '加载中...'}
        </span>
        <ChevronDown className="w-4 h-4 text-gray-500" />
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
          <div className="absolute top-full mt-1 left-0 z-20 w-64 bg-gray-900 border border-gray-700
                          rounded-lg shadow-xl overflow-hidden">
            <div className="max-h-72 overflow-y-auto">
              {episodes.map((ep) => (
                <button
                  key={ep.id}
                  onClick={() => { onEpisodeChange(ep.id); setOpen(false); }}
                  className={`w-full flex items-center gap-3 px-4 py-2.5 text-sm transition-colors
                    ${ep.id === (currentEpisodeId || episodes[0]?.id)
                      ? 'bg-blue-900/30 text-blue-300'
                      : 'text-gray-400 hover:bg-gray-800 hover:text-gray-200'
                    }`}
                >
                  <span className="text-gray-500 w-12 shrink-0">
                    第{ep.episodeNumber}集
                  </span>
                  <span className="flex-1 truncate text-left">{ep.title}</span>
                  {ep.status === 'COMPLETED' && (
                    <span className="shrink-0 text-xs px-1.5 py-0.5 bg-green-900/40 text-green-400 rounded">
                      完成
                    </span>
                  )}
                  {ep.status === 'PROCESSING' && (
                    <span className="shrink-0 text-xs px-1.5 py-0.5 bg-blue-900/40 text-blue-400 rounded">
                      制作中
                    </span>
                  )}
                </button>
              ))}
            </div>
            <div className="border-t border-gray-800 p-2">
              {currentEpisode?.status === 'COMPLETED' ? (
                <button
                  onClick={handleCreateNext}
                  className="w-full flex items-center gap-2 px-3 py-2 text-sm text-blue-400
                             hover:bg-blue-900/20 rounded-lg transition-colors"
                >
                  <PlusCircle className="w-4 h-4" />
                  创建新一集
                </button>
              ) : (
                <div className="px-3 py-2 text-xs text-gray-600 text-center">
                  完成当前剧集后方可创建新一集
                </div>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
}

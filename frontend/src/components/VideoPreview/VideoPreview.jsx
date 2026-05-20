import { useState, useEffect, useRef } from 'react';
import { Play, Loader2, Film, Download } from 'lucide-react';
import { compositionApi } from '../../api/compositionApi.js';

export default function VideoPreview({ projectId, composition }) {
  const [progress, setProgress] = useState(composition?.progress || 0);
  const [status, setStatus] = useState(composition?.status || 'PENDING');
  const [videoUrl, setVideoUrl] = useState(composition?.videoUrl || null);
  const intervalRef = useRef(null);

  useEffect(() => {
    if (status === 'COMPLETED' || status === 'FAILED') return;

    const poll = async () => {
      try {
        const data = await compositionApi.get(projectId);
        setStatus(data.status);
        setProgress(data.progress);
        if (data.videoUrl) setVideoUrl(data.videoUrl);
        if (data.status === 'COMPLETED' || data.status === 'FAILED') {
          clearInterval(intervalRef.current);
        }
      } catch {
        clearInterval(intervalRef.current);
      }
    };

    if (composition?.id) {
      intervalRef.current = setInterval(poll, 3000);
    }
    return () => clearInterval(intervalRef.current);
  }, [projectId, composition?.id, status]);

  const handleCompose = async () => {
    try {
      const result = await compositionApi.submit(projectId);
      setStatus('PROCESSING');
      setProgress(0);
    } catch (e) {
      alert('提交合成失败: ' + e.message);
    }
  };

  if (!composition && status === 'PENDING') {
    return (
      <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 text-center">
        <Film className="w-8 h-8 text-gray-600 mx-auto mb-2" />
        <p className="text-gray-500 mb-4">尚未提交合成</p>
        <button onClick={handleCompose}
                className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg text-sm transition-colors">
          <Play className="w-4 h-4" />
          开始合成
        </button>
      </div>
    );
  }

  return (
    <div className="bg-gray-900 border border-gray-800 rounded-xl p-4">
      <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-3">
        视频预览
      </h3>

      {status !== 'COMPLETED' && (
        <div className="space-y-3">
          <div className="flex items-center gap-2 text-sm">
            <Loader2 className="w-4 h-4 animate-spin text-blue-400" />
            <span className="text-gray-300">
              {status === 'QUEUED' ? '排队中...' :
               status === 'PROCESSING' ? '合成中...' :
               status === 'FAILED' ? '合成失败' : '等待提交'}
            </span>
          </div>
          <div className="w-full h-2 bg-gray-800 rounded-full overflow-hidden">
            <div className="h-full bg-blue-500 rounded-full transition-all duration-700"
                 style={{ width: `${progress}%` }} />
          </div>
          <p className="text-xs text-gray-500">{progress}%</p>
          {status === 'FAILED' && (
            <button onClick={handleCompose}
                    className="px-4 py-2 text-sm bg-red-600 hover:bg-red-700 rounded-lg transition-colors">
              重新合成
            </button>
          )}
        </div>
      )}

      {composition?.coverUrl && (
        <div className="mb-3 max-w-sm mx-auto">
          <p className="text-xs text-gray-500 mb-1">封面图</p>
          <img src={composition.coverUrl} alt="封面"
               className="w-full aspect-[9/16] object-cover rounded-lg bg-gray-800" />
        </div>
      )}

      {videoUrl && (
        <div className="space-y-2 max-w-sm mx-auto">
          <div className="aspect-[9/16] bg-black rounded-lg overflow-hidden">
            <video
              src={videoUrl}
              controls
              className="w-full h-full object-contain"
              poster={composition?.coverUrl}
            />
          </div>
          <a
            href={compositionApi.getDownloadUrl(projectId, composition?.id)}
            download
            className="inline-flex items-center gap-2 px-4 py-2 bg-green-600 hover:bg-green-700 rounded-lg text-sm transition-colors"
          >
            <Download className="w-4 h-4" />
            下载成品 MP4
          </a>
        </div>
      )}
    </div>
  );
}

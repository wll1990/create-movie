import { useState, useEffect } from 'react';
import { Play, Check, SkipForward, RefreshCw, Loader2, Eye, Clapperboard, AlertTriangle } from 'lucide-react';
import { compositionApi } from '../../api/compositionApi.js';
import FrameDetailModal from './FrameDetailModal.jsx';

const STATUS_COLORS = {
  APPROVED: 'bg-green-900/50 text-green-400 border-green-800',
  COMPLETED: 'bg-blue-900/50 text-blue-400 border-blue-800',
  GENERATING: 'bg-yellow-900/50 text-yellow-400 border-yellow-800',
  PROMPT_READY: 'bg-purple-900/50 text-purple-400 border-purple-800',
  FAILED: 'bg-red-900/50 text-red-400 border-red-800',
  SKIPPED: 'bg-gray-700 text-gray-500 border-gray-700',
  PENDING: 'bg-gray-800 text-gray-600 border-gray-700',
};

const STATUS_LABELS = {
  APPROVED: '已通过',
  COMPLETED: '已完成',
  GENERATING: '生成中',
  PROMPT_READY: '待生成',
  FAILED: '失败',
  SKIPPED: '已跳过',
  PENDING: '排队中',
};

export default function ClipGenerationPanel({
  projectId,
  clipProgress,
  onRefresh,
}) {
  const [loadingAction, setLoadingAction] = useState({});
  const [detailFrameId, setDetailFrameId] = useState(null);
  const [prerequisites, setPrerequisites] = useState(null);

  useEffect(() => {
    compositionApi.getClipPrerequisites(projectId)
      .then(setPrerequisites)
      .catch(() => {});
  }, [projectId]);

  const frames = clipProgress?.frames || [];
  const totalFrames = clipProgress?.totalFrames || 0;
  const approvedFrames = clipProgress?.approvedFrames || 0;
  const completedFrames = clipProgress?.completedFrames || 0;
  const failedFrames = clipProgress?.failedFrames || 0;
  const skippedFrames = clipProgress?.skippedFrames || 0;
  const doneFrames = approvedFrames + skippedFrames;

  const overallProgress = totalFrames > 0
    ? Math.round((doneFrames / totalFrames) * 100)
    : 0;

  const handleAction = async (action, frameId) => {
    setLoadingAction((prev) => ({ ...prev, [frameId + action]: true }));
    try {
      switch (action) {
        case 'generate':
          await compositionApi.generateFrameClip(projectId, frameId);
          break;
        case 'approve':
          await compositionApi.approveFrame(projectId, frameId);
          break;
        case 'skip':
          await compositionApi.skipFrame(projectId, frameId);
          break;
        case 'retry':
          await compositionApi.retryFrame(projectId, frameId);
          break;
      }
      onRefresh?.();
    } catch (e) {
      alert('操作失败: ' + (e.response?.data?.message || e.message));
    } finally {
      setLoadingAction((prev) => ({ ...prev, [frameId + action]: false }));
    }
  };

  return (
    <div className="bg-gray-900 border border-blue-800 rounded-xl p-4">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <Clapperboard className="w-4 h-4 text-blue-400" />
          <h3 className="text-sm font-semibold text-blue-300 uppercase tracking-wider">
            视频片段生成
          </h3>
          <span className="text-xs text-blue-400">
            {doneFrames}/{totalFrames}
          </span>
        </div>
      </div>

      {/* Prerequisites warning */}
      {prerequisites && !prerequisites.ready && (
        <div className="mb-4 p-3 bg-yellow-900/20 border border-yellow-800/50 rounded-lg">
          <div className="flex items-start gap-2">
            <AlertTriangle className="w-4 h-4 text-yellow-400 shrink-0 mt-0.5" />
            <div>
              <p className="text-xs text-yellow-400 font-medium">
                {prerequisites.message || '前置条件未满足'}
              </p>
              {prerequisites.characters && (
                <div className="mt-1 space-y-0.5">
                  {prerequisites.characters.filter(c => !c.hasReferenceImage).map(c => (
                    <p key={c.id} className="text-xs text-gray-400">
                      {c.name}: {c.imageStatus === 'PENDING' ? '尚未开始生成立绘' :
                        c.imageStatus === 'GENERATING' ? '立绘生成中...' :
                        c.imageStatus === 'CANDIDATES_READY' ? '请选择最佳立绘' :
                        '缺少参考图'}
                    </p>
                  ))}
                </div>
              )}
              <p className="text-xs text-gray-500 mt-1">
                请在角色卡片中完成"立绘抽卡"→"三视图"→"表情选择"后，再生成视频片段
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Overall progress bar */}
      <div className="mb-4">
        <div className="flex items-center justify-between text-xs mb-1">
          <div className="flex items-center gap-2">
            <span className="text-green-400">通过 {approvedFrames}</span>
            <span className="text-blue-400">完成 {completedFrames}</span>
            <span className="text-red-400">失败 {failedFrames}</span>
            <span className="text-gray-500">跳过 {skippedFrames}</span>
          </div>
          <span className="text-gray-400">{overallProgress}%</span>
        </div>
        <div className="w-full h-2 bg-gray-800 rounded-full overflow-hidden flex">
          {approvedFrames > 0 && (
            <div className="h-full bg-green-500 transition-all"
                 style={{ width: `${(approvedFrames / totalFrames) * 100}%` }} />
          )}
          {skippedFrames > 0 && (
            <div className="h-full bg-gray-500 transition-all"
                 style={{ width: `${(skippedFrames / totalFrames) * 100}%` }} />
          )}
          {failedFrames > 0 && (
            <div className="h-full bg-red-500 transition-all"
                 style={{ width: `${(failedFrames / totalFrames) * 100}%` }} />
          )}
        </div>
      </div>

      {/* Frame list */}
      <div className="space-y-2 max-h-[500px] overflow-y-auto">
        {frames.map((frame) => {
          const status = frame.status;
          const isCurrent = status === 'PROMPT_READY' || status === 'GENERATING';
          const isLoading = loadingAction[frame.frameId + 'generate']
            || loadingAction[frame.frameId + 'approve']
            || loadingAction[frame.frameId + 'skip']
            || loadingAction[frame.frameId + 'retry'];

          return (
            <div
              key={frame.frameId || frame.frameNumber}
              className={`flex items-center gap-3 p-3 rounded-lg border transition-colors ${
                isCurrent ? 'border-blue-600 bg-blue-900/10' :
                STATUS_COLORS[status] || 'bg-gray-800 border-gray-700'
              }`}
            >
              {/* Thumbnails */}
              <div className="flex gap-1 shrink-0">
                {frame.referenceImageUrl && (
                  <img src={frame.referenceImageUrl} alt="角色参考"
                       className="w-10 h-10 object-cover rounded bg-gray-700"
                       title="角色参考图" />
                )}
                {frame.expressionImageUrl && (
                  <img src={frame.expressionImageUrl} alt="表情参考"
                       className="w-10 h-10 object-cover rounded bg-gray-700"
                       title="表情参考图" />
                )}
                {frame.backgroundImageUrl && (
                  <img src={frame.backgroundImageUrl} alt="场景背景"
                       className="w-10 h-10 object-cover rounded bg-gray-700"
                       title="场景背景图" />
                )}
              </div>

              {/* Frame info */}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold text-gray-300">
                    帧 #{frame.frameNumber}
                  </span>
                  <span className={`text-[10px] px-1.5 py-0.5 rounded ${
                    STATUS_COLORS[status] || 'bg-gray-700 text-gray-400'
                  }`}>
                    {STATUS_LABELS[status] || status}
                    {status === 'GENERATING' && (
                      <Loader2 className="w-3 h-3 animate-spin inline ml-1" />
                    )}
                  </span>
                </div>
                {/* Truncated prompt preview */}
                <p className="text-xs text-gray-600 truncate mt-0.5">
                  {frame.clipPrompt?.substring(0, 80) || '无提示词'}
                </p>
              </div>

              {/* Action buttons */}
              <div className="flex items-center gap-1 shrink-0">
                <button
                  onClick={() => setDetailFrameId(frame.frameId)}
                  className="p-1.5 hover:bg-gray-600 rounded transition-colors"
                  title="查看详情"
                >
                  <Eye className="w-3.5 h-3.5 text-gray-400" />
                </button>

                {status === 'PROMPT_READY' && (
                  <button
                    onClick={() => handleAction('generate', frame.frameId)}
                    disabled={isLoading || (prerequisites && !prerequisites.ready)}
                    title={prerequisites && !prerequisites.ready ? '请先生成角色立绘和三视图' : '生成视频片段'}
                    className="p-1.5 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 rounded transition-colors"
                    title="生成"
                  >
                    <Play className="w-3.5 h-3.5" />
                  </button>
                )}

                {status === 'COMPLETED' && (
                  <button
                    onClick={() => handleAction('approve', frame.frameId)}
                    disabled={isLoading}
                    className="p-1.5 bg-green-600 hover:bg-green-700 disabled:opacity-50 rounded transition-colors"
                    title="通过"
                  >
                    <Check className="w-3.5 h-3.5" />
                  </button>
                )}

                {status === 'FAILED' && (
                  <button
                    onClick={() => handleAction('retry', frame.frameId)}
                    disabled={isLoading}
                    className="p-1.5 bg-yellow-600 hover:bg-yellow-700 disabled:opacity-50 rounded transition-colors"
                    title="重试"
                  >
                    <RefreshCw className="w-3.5 h-3.5" />
                  </button>
                )}

                {status !== 'APPROVED' && status !== 'SKIPPED' && status !== 'PENDING' && (
                  <button
                    onClick={() => handleAction('skip', frame.frameId)}
                    disabled={isLoading}
                    className="p-1.5 hover:bg-gray-600 rounded transition-colors"
                    title="跳过"
                  >
                    <SkipForward className="w-3.5 h-3.5 text-gray-500" />
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Frame detail modal */}
      {detailFrameId && (
        <FrameDetailModal
          projectId={projectId}
          frameId={detailFrameId}
          onClose={() => setDetailFrameId(null)}
          onAction={onRefresh}
        />
      )}
    </div>
  );
}

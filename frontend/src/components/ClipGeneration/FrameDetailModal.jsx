import { useState, useEffect } from 'react';
import { Loader2, Play, RefreshCw, Check, SkipForward, Eye, Save } from 'lucide-react';
import { compositionApi } from '../../api/compositionApi.js';

const SHOT_LABELS = {
  ECU: '大特写', CU: '特写', MCU: '中近景',
  MS: '中景', FS: '全景', LS: '远景',
};

export default function FrameDetailModal({
  projectId,
  frameId,
  onClose,
  onAction,
}) {
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [editingPrompt, setEditingPrompt] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    loadDetail();
  }, [frameId]);

  const loadDetail = async () => {
    setLoading(true);
    try {
      const data = await compositionApi.getFrameDetail(projectId, frameId);
      setDetail(data);
      setEditingPrompt(data.clipPrompt || '');
    } catch (e) {
      console.error('Failed to load frame detail:', e);
    } finally {
      setLoading(false);
    }
  };

  const handleSavePrompt = async () => {
    setSaving(true);
    try {
      await compositionApi.updateFramePrompt(projectId, frameId, editingPrompt);
      setDetail({ ...detail, clipPrompt: editingPrompt });
      onAction?.();
    } catch (e) {
      alert('保存失败: ' + (e.response?.data?.message || e.message));
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60"
           onClick={onClose}>
        <div className="bg-gray-900 border border-gray-700 rounded-xl p-8"
             onClick={(e) => e.stopPropagation()}>
          <Loader2 className="w-6 h-6 animate-spin text-blue-400 mx-auto" />
          <p className="text-sm text-gray-500 mt-2">加载帧详情...</p>
        </div>
      </div>
    );
  }

  if (!detail) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60"
         onClick={onClose}>
      <div className="bg-gray-900 border border-gray-700 rounded-xl w-full max-w-3xl mx-4 max-h-[90vh] overflow-y-auto"
           onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-gray-700 sticky top-0 bg-gray-900 z-10">
          <div>
            <h3 className="text-lg font-semibold">
              帧 #{detail.frameNumber} 详情
            </h3>
            <span className="text-xs text-gray-500">
              {SHOT_LABELS[detail.shotType] || detail.shotType} · {detail.cameraAngle || '平视'} · {detail.durationSec}s
            </span>
          </div>
          <button onClick={onClose} className="p-1 hover:bg-gray-700 rounded-lg transition-colors">
            <span className="text-gray-400 text-lg">&times;</span>
          </button>
        </div>

        <div className="p-4 space-y-4">
          {/* Reference Images */}
          <div className="grid grid-cols-3 gap-3">
            <div>
              <p className="text-xs text-gray-500 mb-1">角色参考图</p>
              {detail.referenceImageUrl ? (
                <img src={detail.referenceImageUrl} alt="角色参考"
                     className="w-full aspect-[3/4] object-cover rounded-lg bg-gray-800" />
              ) : (
                <div className="w-full aspect-[3/4] bg-gray-800 rounded-lg flex items-center justify-center">
                  <Eye className="w-5 h-5 text-gray-600" />
                </div>
              )}
            </div>
            <div>
              <p className="text-xs text-gray-500 mb-1">表情参考图</p>
              {detail.expressionImageUrl ? (
                <img src={detail.expressionImageUrl} alt="表情参考"
                     className="w-full aspect-[3/4] object-cover rounded-lg bg-gray-800" />
              ) : (
                <div className="w-full aspect-[3/4] bg-gray-800 rounded-lg flex items-center justify-center">
                  <Eye className="w-5 h-5 text-gray-600" />
                </div>
              )}
            </div>
            <div>
              <p className="text-xs text-gray-500 mb-1">场景背景</p>
              {detail.backgroundImageUrl ? (
                <img src={detail.backgroundImageUrl} alt="场景背景"
                     className="w-full aspect-[16/9] object-cover rounded-lg bg-gray-800" />
              ) : (
                <div className="w-full aspect-[16/9] bg-gray-800 rounded-lg flex items-center justify-center">
                  <Eye className="w-5 h-5 text-gray-600" />
                </div>
              )}
            </div>
          </div>

          {/* Frame info */}
          <div className="grid grid-cols-2 gap-2 text-xs">
            <div className="bg-gray-800 rounded-lg p-2">
              <span className="text-gray-500">景别: </span>
              <span className="text-gray-300">{SHOT_LABELS[detail.shotType] || detail.shotType}</span>
            </div>
            <div className="bg-gray-800 rounded-lg p-2">
              <span className="text-gray-500">机位: </span>
              <span className="text-gray-300">{detail.cameraAngle || '平视'}</span>
            </div>
            <div className="bg-gray-800 rounded-lg p-2">
              <span className="text-gray-500">时长: </span>
              <span className="text-gray-300">{detail.durationSec}s</span>
            </div>
            <div className="bg-gray-800 rounded-lg p-2">
              <span className="text-gray-500">状态: </span>
              <span className="text-gray-300">{detail.status}</span>
            </div>
          </div>

          {/* Background description */}
          {detail.bgDescription && (
            <div>
              <p className="text-xs text-gray-500 mb-1">背景描述</p>
              <p className="text-xs text-gray-300 bg-gray-800 rounded-lg p-2">{detail.bgDescription}</p>
            </div>
          )}

          {/* Characters */}
          {(detail.characters || []).length > 0 && (
            <div>
              <p className="text-xs text-gray-500 mb-1">出场角色</p>
              <div className="flex flex-wrap gap-2">
                {detail.characters.map((c, i) => (
                  <span key={i} className="text-xs px-2 py-1 bg-gray-800 rounded-lg text-gray-300">
                    {c.characterName} ({c.expression || 'neutral'})
                  </span>
                ))}
              </div>
            </div>
          )}

          {/* Dialogue */}
          {detail.subtitleText && (
            <div>
              <p className="text-xs text-gray-500 mb-1">台词</p>
              <p className="text-sm text-gray-300 italic bg-gray-800 rounded-lg p-2">
                "{detail.subtitleText}"
              </p>
            </div>
          )}

          {/* Clip Prompt */}
          <div>
            <div className="flex items-center justify-between mb-1">
              <p className="text-xs text-gray-500">视频生成提示词</p>
              <button
                onClick={handleSavePrompt}
                disabled={saving}
                className="inline-flex items-center gap-1 px-2 py-1 text-xs bg-blue-600 hover:bg-blue-700 disabled:opacity-50 rounded transition-colors"
              >
                {saving ? <Loader2 className="w-3 h-3 animate-spin" /> : <Save className="w-3 h-3" />}
                保存
              </button>
            </div>
            <textarea
              value={editingPrompt}
              onChange={(e) => setEditingPrompt(e.target.value)}
              rows={6}
              className="w-full text-xs bg-gray-800 text-gray-200 font-mono rounded-lg p-3 border border-gray-700 focus:border-blue-500 resize-y"
            />
          </div>

          {/* Video Preview */}
          {detail.clipVideoUrl && (
            <div>
              <p className="text-xs text-gray-500 mb-1">视频预览</p>
              <video
                src={detail.clipVideoUrl}
                controls
                className="w-full rounded-lg bg-black"
                style={{ maxHeight: '300px' }}
              />
            </div>
          )}

          {/* Error message */}
          {detail.errorMessage && (
            <div className="bg-red-900/20 border border-red-800/50 rounded-lg p-3">
              <p className="text-xs text-red-400">{detail.errorMessage}</p>
            </div>
          )}
        </div>

        {/* Action buttons */}
        <div className="flex items-center gap-2 p-4 border-t border-gray-700 sticky bottom-0 bg-gray-900">
          {detail.status === 'PROMPT_READY' && (
            <button
              onClick={async () => {
                await compositionApi.generateFrameClip(projectId, frameId);
                onAction?.();
                loadDetail();
              }}
              className="flex-1 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg text-sm transition-colors"
            >
              <Play className="w-4 h-4 inline mr-1" /> 生成
            </button>
          )}
          {detail.status === 'COMPLETED' && (
            <button
              onClick={async () => {
                await compositionApi.approveFrame(projectId, frameId);
                onAction?.();
                onClose();
              }}
              className="flex-1 py-2 bg-green-600 hover:bg-green-700 rounded-lg text-sm transition-colors"
            >
              <Check className="w-4 h-4 inline mr-1" /> 通过
            </button>
          )}
          {detail.status === 'FAILED' && (
            <button
              onClick={async () => {
                await compositionApi.retryFrame(projectId, frameId);
                onAction?.();
                loadDetail();
              }}
              className="flex-1 py-2 bg-yellow-600 hover:bg-yellow-700 rounded-lg text-sm transition-colors"
            >
              <RefreshCw className="w-4 h-4 inline mr-1" /> 重试
            </button>
          )}
          {detail.status !== 'APPROVED' && detail.status !== 'SKIPPED' && (
            <button
              onClick={async () => {
                await compositionApi.skipFrame(projectId, frameId);
                onAction?.();
                onClose();
              }}
              className="flex-1 py-2 bg-gray-600 hover:bg-gray-500 rounded-lg text-sm transition-colors"
            >
              <SkipForward className="w-4 h-4 inline mr-1" /> 跳过
            </button>
          )}
          <button
            onClick={onClose}
            className="flex-1 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm transition-colors"
          >
            关闭
          </button>
        </div>
      </div>
    </div>
  );
}

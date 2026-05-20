import { useState } from 'react';
import { Grid3X3, Loader2, Sparkles, Eye, Edit3, Save, X, RefreshCw, Image } from 'lucide-react';
import { storyboardApi } from '../../api/storyboardApi.js';

const SHOT_LABELS = {
  ECU: '大特写', CU: '特写', MCU: '中近景',
  MS: '中景', FS: '全景', LS: '远景',
};

const SHOT_OPTIONS = ['ECU', 'CU', 'MCU', 'MS', 'FS', 'LS'];

const CLIP_STATUS_COLORS = {
  APPROVED: 'bg-green-900/50 text-green-400',
  COMPLETED: 'bg-blue-900/50 text-blue-400',
  GENERATING: 'bg-yellow-900/50 text-yellow-400',
  PROMPT_READY: 'bg-purple-900/50 text-purple-400',
  FAILED: 'bg-red-900/50 text-red-400',
  PENDING: 'bg-gray-700 text-gray-400',
  SKIPPED: 'bg-gray-700 text-gray-500',
};

export default function StoryboardEditor({ storyboard, onGenerate, loading, projectId, characters = [] }) {
  const frames = storyboard?.frames || [];
  const charMap = {};
  characters.forEach(c => { charMap[c.name] = c; });
  const [editingFrame, setEditingFrame] = useState(null);
  const [editData, setEditData] = useState({});
  const [saving, setSaving] = useState(false);
  const [showPrompt, setShowPrompt] = useState(null); // 'storyboard' | {frameId, type: 'clip'|'storyboard'}
  const [promptContent, setPromptContent] = useState('');
  const [loadingPrompt, setLoadingPrompt] = useState(false);
  const [regeneratingFrame, setRegeneratingFrame] = useState(null);

  const handleEdit = (frame) => {
    setEditingFrame(frame.id);
    setEditData({
      shotType: frame.shotType || 'MS',
      cameraAngle: frame.cameraAngle || '平视',
      subtitleText: frame.subtitleText || '',
      durationSec: frame.durationSec || 3.0,
      transition: frame.transition || 'cut',
      bgDescription: frame.bgDescription || '',
    });
  };

  const handleSave = async (frameId) => {
    setSaving(true);
    try {
      await storyboardApi.updateFrame(projectId, frameId, editData);
      setEditingFrame(null);
      // Reload storyboard to get updated data
      if (onGenerate) onGenerate();
    } catch (e) {
      alert('保存失败: ' + (e.response?.data?.message || e.message));
    } finally {
      setSaving(false);
    }
  };

  const handleViewStoryboardPrompt = async () => {
    setLoadingPrompt(true);
    try {
      const data = await storyboardApi.getStoryboardPrompt(projectId);
      setPromptContent(data.prompt || '暂无提示词');
      setShowPrompt('storyboard');
    } catch {
      setPromptContent('获取失败');
      setShowPrompt('storyboard');
    } finally {
      setLoadingPrompt(false);
    }
  };

  const handleViewFramePrompt = async (frameId) => {
    setLoadingPrompt(true);
    try {
      const data = await storyboardApi.getFramePrompt(projectId, frameId);
      setPromptContent(data.clipPrompt || '暂无提示词');
      setShowPrompt({ frameId, type: 'clip' });
    } catch {
      setPromptContent('获取失败');
      setShowPrompt({ frameId, type: 'clip' });
    } finally {
      setLoadingPrompt(false);
    }
  };

  const handleRegenerateFrame = async (frameId) => {
    setRegeneratingFrame(frameId);
    try {
      await storyboardApi.regenerateFrame(projectId, frameId);
      if (onGenerate) onGenerate();
    } catch (e) {
      alert('重新生成失败: ' + (e.response?.data?.message || e.message));
    } finally {
      setRegeneratingFrame(null);
    }
  };

  const handleRegenerateAll = async () => {
    if (!confirm('确定要重新生成全部分镜吗？这会覆盖当前所有分镜。')) return;
    setRegeneratingFrame('all');
    try {
      await storyboardApi.regenerateAll(projectId);
      if (onGenerate) onGenerate();
    } catch (e) {
      alert('重新生成失败: ' + (e.response?.data?.message || e.message));
    } finally {
      setRegeneratingFrame(null);
    }
  };

  return (
    <div className="bg-gray-900 border border-gray-800 rounded-xl p-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider">
          分镜 · 共{storyboard?.totalFrames || 0}帧
        </h3>
        <div className="flex items-center gap-2">
          <button
            onClick={handleViewStoryboardPrompt}
            disabled={loadingPrompt}
            className="inline-flex items-center gap-1 px-2 py-1.5 text-xs bg-gray-700 hover:bg-gray-600 rounded-lg transition-colors"
          >
            <Eye className="w-3 h-3" /> 分镜提示词
          </button>
          <button
            onClick={handleRegenerateAll}
            disabled={regeneratingFrame === 'all'}
            className="inline-flex items-center gap-1 px-3 py-1.5 text-xs bg-yellow-700 hover:bg-yellow-600 disabled:opacity-50 rounded-lg transition-colors"
          >
            {regeneratingFrame === 'all' ? <Loader2 className="w-3 h-3 animate-spin" /> : <RefreshCw className="w-3 h-3" />}
            全部重新生成
          </button>
          <button
            onClick={onGenerate}
            disabled={loading}
            className="inline-flex items-center gap-1 px-3 py-1.5 text-xs bg-blue-600 hover:bg-blue-700 disabled:opacity-50 rounded-lg transition-colors"
          >
            {loading ? <Loader2 className="w-3 h-3 animate-spin" /> : <Sparkles className="w-3 h-3" />}
            生成分镜
          </button>
        </div>
      </div>

      {frames.length === 0 ? (
        <div className="text-center py-8 text-gray-500">
          <Grid3X3 className="w-8 h-8 mx-auto mb-2 opacity-50" />
          <p>尚未生成分镜</p>
        </div>
      ) : (
        <div className="grid grid-cols-2 lg:grid-cols-3 gap-3">
          {frames.map((frame) => {
            const isEditing = editingFrame === frame.id;
            return (
              <div key={frame.id || frame.frameNumber}
                   className={`bg-gray-800 rounded-lg border transition-colors ${
                     isEditing ? 'border-blue-600' : 'border-gray-700 hover:border-blue-600'
                   }`}>
                {/* Background thumbnail */}
                {frame.bgImageUrl && (
                  <div className="relative h-24 overflow-hidden rounded-t-lg">
                    <img src={frame.bgImageUrl} alt="背景"
                         className="w-full h-full object-cover" />
                    <div className="absolute inset-0 bg-gradient-to-t from-gray-800 to-transparent" />
                  </div>
                )}

                <div className="p-3">
                  {/* Frame header */}
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-bold text-blue-400">
                      #{frame.frameNumber}
                    </span>
                    <div className="flex items-center gap-1">
                      {isEditing ? (
                        <select
                          value={editData.shotType}
                          onChange={(e) => setEditData({ ...editData, shotType: e.target.value })}
                          className="text-xs px-1.5 py-0.5 bg-gray-700 rounded text-gray-300 border border-gray-600"
                        >
                          {SHOT_OPTIONS.map(s => (
                            <option key={s} value={s}>{SHOT_LABELS[s] || s}</option>
                          ))}
                        </select>
                      ) : (
                        <span className="text-xs px-1.5 py-0.5 bg-gray-700 rounded text-gray-400">
                          {SHOT_LABELS[frame.shotType] || frame.shotType}
                        </span>
                      )}
                      {frame.transition !== 'cut' && (
                        <span className="text-xs px-1.5 py-0.5 bg-purple-900/50 rounded text-purple-400">
                          {isEditing ? (
                            <input value={editData.transition} onChange={(e) => setEditData({ ...editData, transition: e.target.value })}
                                   className="w-10 bg-transparent text-purple-400" />
                          ) : frame.transition}
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Clip status badge */}
                  {frame.clipStatus && (
                    <span className={`inline-block text-[10px] px-1.5 py-0.5 rounded mb-2 ${CLIP_STATUS_COLORS[frame.clipStatus] || 'bg-gray-700 text-gray-400'}`}>
                      {frame.clipStatus}
                    </span>
                  )}

                  {/* BG description */}
                  {isEditing ? (
                    <textarea
                      value={editData.bgDescription}
                      onChange={(e) => setEditData({ ...editData, bgDescription: e.target.value })}
                      rows={2}
                      className="w-full text-xs bg-gray-700 text-gray-200 rounded p-1.5 border border-gray-600 resize-none mb-2"
                    />
                  ) : (
                    <p className="text-xs text-gray-500 mb-2 line-clamp-2">
                      {frame.bgDescription || '无背景'}
                    </p>
                  )}

                  {/* Characters */}
                  {(frame.characters || []).length > 0 && (
                    <div className="space-y-1 mb-2">
                      {frame.characters.map((char, idx) => {
                        const charData = charMap[char.characterName];
                        const refUrl = charData?.appearance?.referenceImageUrl || charData?.appearance?.portraitUrl;
                        return (
                        <div key={idx}>
                          <div className="flex items-center gap-2 text-xs">
                            <span className="text-gray-300">{char.characterName}</span>
                            <span className={`px-1 py-0.5 rounded text-[10px] ${
                              char.expression === 'happy' ? 'bg-yellow-900/50 text-yellow-400' :
                              char.expression === 'sad' ? 'bg-blue-900/50 text-blue-400' :
                              char.expression === 'angry' ? 'bg-red-900/50 text-red-400' :
                              'bg-gray-700 text-gray-400'
                            }`}>
                              {char.expression || 'neutral'}
                            </span>
                          </div>
                          {refUrl && (
                            <img src={refUrl} alt={char.characterName}
                                 className="w-12 h-12 object-cover rounded mt-1 bg-gray-700"
                                 onClick={() => window.open(refUrl, '_blank')}
                                 title={`${char.characterName} 参考图`} />
                          )}
                        </div>
                      )})}
                    </div>
                  )}

                  {/* Dialogue */}
                  {isEditing ? (
                    <input
                      value={editData.subtitleText}
                      onChange={(e) => setEditData({ ...editData, subtitleText: e.target.value })}
                      className="w-full text-xs bg-gray-700 text-gray-200 italic rounded p-1.5 border border-gray-600 mb-2"
                      placeholder="台词..."
                    />
                  ) : (
                    frame.subtitleText && (
                      <div className="text-xs text-gray-400 italic mt-2 pt-2 border-t border-gray-700/50 line-clamp-2">
                        "{frame.subtitleText}"
                      </div>
                    )
                  )}

                  {/* Duration + camera angle */}
                  <div className="flex items-center justify-between text-xs text-gray-600 mt-2">
                    <span>
                      机位: {isEditing ? (
                        <input value={editData.cameraAngle}
                               onChange={(e) => setEditData({ ...editData, cameraAngle: e.target.value })}
                               className="w-16 bg-gray-700 text-gray-200 rounded px-1 py-0.5 border border-gray-600" />
                      ) : (frame.cameraAngle || '平视')}
                    </span>
                    <span>
                      {isEditing ? (
                        <input type="number" value={editData.durationSec}
                               onChange={(e) => setEditData({ ...editData, durationSec: Number(e.target.value) })}
                               min={1} max={30} step={0.5}
                               className="w-12 bg-gray-700 text-gray-200 rounded px-1 py-0.5 border border-gray-600 text-right" />
                      ) : (frame.durationSec || 3).toFixed(1)}s
                    </span>
                  </div>

                  {/* Action buttons */}
                  <div className="flex items-center gap-1 mt-3 pt-2 border-t border-gray-700/50">
                    {isEditing ? (
                      <>
                        <button onClick={() => handleSave(frame.id)} disabled={saving}
                                className="inline-flex items-center gap-1 px-2 py-1 text-xs bg-green-600 hover:bg-green-700 rounded transition-colors">
                          <Save className="w-3 h-3" /> 保存
                        </button>
                        <button onClick={() => setEditingFrame(null)}
                                className="inline-flex items-center gap-1 px-2 py-1 text-xs bg-gray-600 hover:bg-gray-500 rounded transition-colors">
                          <X className="w-3 h-3" /> 取消
                        </button>
                      </>
                    ) : (
                      <>
                        <button onClick={() => handleEdit(frame)}
                                className="inline-flex items-center gap-1 px-2 py-1 text-xs bg-gray-700 hover:bg-gray-600 rounded transition-colors">
                          <Edit3 className="w-3 h-3" /> 编辑
                        </button>
                        <button onClick={() => handleViewFramePrompt(frame.id)}
                                className="inline-flex items-center gap-1 px-2 py-1 text-xs bg-gray-700 hover:bg-gray-600 rounded transition-colors">
                          <Eye className="w-3 h-3" /> 提示词
                        </button>
                        <button onClick={() => handleRegenerateFrame(frame.id)}
                                disabled={regeneratingFrame === frame.id}
                                className="inline-flex items-center gap-1 px-2 py-1 text-xs bg-gray-700 hover:bg-gray-600 disabled:opacity-50 rounded transition-colors">
                          {regeneratingFrame === frame.id
                            ? <Loader2 className="w-3 h-3 animate-spin" />
                            : <RefreshCw className="w-3 h-3" />}
                          重生成
                        </button>
                      </>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Prompt Modal */}
      {showPrompt && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60"
             onClick={() => { setShowPrompt(null); setPromptContent(''); }}>
          <div className="bg-gray-900 border border-gray-700 rounded-xl w-full max-w-2xl mx-4 max-h-[85vh] overflow-y-auto"
               onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between p-4 border-b border-gray-700">
              <h3 className="text-lg font-semibold">
                {showPrompt === 'storyboard' ? '分镜生成提示词' : '视频生成提示词'}
              </h3>
              <button onClick={() => { setShowPrompt(null); setPromptContent(''); }}
                      className="p-1 hover:bg-gray-700 rounded-lg transition-colors">
                <span className="text-gray-400 text-lg">&times;</span>
              </button>
            </div>
            <div className="p-4">
              <pre className="text-xs text-gray-300 whitespace-pre-wrap font-mono bg-gray-800 rounded-lg p-3 max-h-96 overflow-y-auto">
                {promptContent}
              </pre>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

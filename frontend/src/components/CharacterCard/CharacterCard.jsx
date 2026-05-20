import { useState, useEffect, useCallback, useRef } from 'react';
import { Loader2, Check, RefreshCw, Image, Eye, Volume2, X, ChevronDown, ChevronUp } from 'lucide-react';
import { characterApi } from '../../api/characterApi.js';
import { ttsApi } from '../../api/compositionApi.js';

export default function CharacterCard({
  char,
  projectId,
  voiceList,
  onVoiceChange,
  onViewPrompt,
}) {
  const [imageStatus, setImageStatus] = useState(null);
  const [showGallery, setShowGallery] = useState(false);
  const [selecting, setSelecting] = useState(false);
  const [previewPlaying, setPreviewPlaying] = useState(false);
  const [showExpressions, setShowExpressions] = useState(false);
  const [selectingExpr, setSelectingExpr] = useState(null);
  const [threeViewLoading, setThreeViewLoading] = useState(false);
  const [exprRegenerating, setExprRegenerating] = useState(false);
  const audioRef = useRef(null);

  const pollImageStatus = useCallback(async () => {
    try {
      const status = await characterApi.getImageStatus(projectId, char.id);
      setImageStatus(status);
      return status;
    } catch {
      return null;
    }
  }, [projectId, char.id]);

  useEffect(() => {
    const status = char.imageGenerationStatus;
    if (!status || status === 'COMPLETED' || status === 'FAILED'
        || status === 'EXPRESSIONS_READY' || status === 'EXPRESSIONS_FAILED'
        || status === 'THREEVIEW_FAILED') {
      pollImageStatus();
      return;
    }
    const interval = setInterval(pollImageStatus, 3000);
    pollImageStatus();
    return () => clearInterval(interval);
  }, [char.imageGenerationStatus, pollImageStatus]);

  // --- Voice preview ---
  const handlePreviewVoice = async () => {
    setPreviewPlaying(true);
    try {
      const voice = char.voiceConfig?.voice || 'zh-CN-XiaoxiaoNeural';
      const speed = char.voiceConfig?.speed || 1.0;
      const text = `你好，我是${char.name}`;
      const blob = await ttsApi.previewVoice(projectId, { text, voice, speed });
      const url = URL.createObjectURL(blob);
      if (audioRef.current) {
        audioRef.current.src = url;
        audioRef.current.play();
      }
    } catch (e) {
      console.error('Voice preview failed:', e);
    } finally {
      setPreviewPlaying(false);
    }
  };

  const handleSelectPortrait = async (candidateIndex) => {
    setSelecting(true);
    try {
      await characterApi.selectPortrait(projectId, char.id, candidateIndex);
      setShowGallery(false);
      pollImageStatus();
    } catch (e) {
      alert('选择失败: ' + (e.response?.data?.message || e.message));
    } finally {
      setSelecting(false);
    }
  };

  const handleRegenerateThreeView = async () => {
    setThreeViewLoading(true);
    try {
      await characterApi.regenerateThreeView(projectId, char.id);
      pollImageStatus();
    } catch (e) {
      alert('重新生成失败: ' + (e.response?.data?.message || e.message));
    } finally {
      setThreeViewLoading(false);
    }
  };

  const handleSelectExpression = async (emotionType, candidateIndex) => {
    setSelectingExpr(emotionType);
    try {
      await characterApi.selectExpression(projectId, char.id, emotionType, candidateIndex);
      pollImageStatus();
    } catch (e) {
      alert('选择失败: ' + (e.response?.data?.message || e.message));
    } finally {
      setSelectingExpr(null);
    }
  };

  const handleRegenerateExpressions = async () => {
    setExprRegenerating(true);
    try {
      await characterApi.regenerateExpressions(projectId, char.id);
      pollImageStatus();
    } catch (e) {
      alert('重新生成失败: ' + (e.response?.data?.message || e.message));
    } finally {
      setExprRegenerating(false);
    }
  };

  const status = imageStatus?.imageGenerationStatus || char.imageGenerationStatus || 'PENDING';
  const candidates = imageStatus?.candidatePortraits || char.candidatePortraits || [];
  const portraitUrl = imageStatus?.portraitUrl || char.appearance?.portraitUrl;
  const threeViewUrl = imageStatus?.threeViewUrl || char.appearance?.threeViewUrl;
  const expressions = imageStatus?.expressions || char.expressions || [];

  const statusInfo = {
    PENDING: { color: 'text-gray-400', bg: 'bg-gray-700', label: '等待立绘抽卡', icon: Image },
    GENERATING: { color: 'text-blue-400', bg: 'bg-blue-900/30', label: '正在生成立绘候选...', icon: Loader2 },
    CANDIDATES_READY: { color: 'text-yellow-400', bg: 'bg-yellow-900/30', label: '请选择最佳立绘', icon: Image },
    THREEVIEW_GENERATING: { color: 'text-purple-400', bg: 'bg-purple-900/30', label: '正在生成三视图...', icon: Loader2 },
    THREEVIEW_FAILED: { color: 'text-red-400', bg: 'bg-red-900/30', label: '三视图生成失败', icon: RefreshCw },
    EXPRESSIONS_GENERATING: { color: 'text-purple-400', bg: 'bg-purple-900/30', label: '正在生成表情候选...', icon: Loader2 },
    EXPRESSIONS_READY: { color: 'text-yellow-400', bg: 'bg-yellow-900/30', label: '请选择各情绪表情', icon: Image },
    EXPRESSIONS_FAILED: { color: 'text-red-400', bg: 'bg-red-900/30', label: '表情生成失败', icon: RefreshCw },
    COMPLETED: { color: 'text-green-400', bg: 'bg-green-900/30', label: '角色图片已完成', icon: Check },
    FAILED: { color: 'text-red-400', bg: 'bg-red-900/30', label: '图像生成失败', icon: RefreshCw },
  };
  const si = statusInfo[status] || statusInfo.PENDING;
  const StatusIcon = si.icon;

  // --- Gallery modal (4 choose 1) ---
  if (showGallery) {
    return (
      <div className="bg-gray-800 rounded-lg border border-yellow-700 p-4">
        <div className="font-medium text-white text-sm mb-1">{char.name} · 选择最佳立绘</div>
        <p className="text-xs text-gray-500 mb-3">点击选择最符合角色形象的一张</p>
        <div className="grid grid-cols-2 gap-3">
          {candidates.map((c, i) => (
            <div key={i}
                 className="relative cursor-pointer group"
                 onClick={() => !selecting && handleSelectPortrait(i)}>
              <img src={c.localUrl || c.ossUrl || c.url} alt={`候选${i + 1}`}
                   className="w-full aspect-[3/4] object-cover rounded-lg bg-gray-700 group-hover:ring-2 ring-yellow-400 transition-all" />
              <div className="absolute inset-0 bg-black/0 group-hover:bg-black/30 rounded-lg transition-all flex items-center justify-center">
                <Check className="w-6 h-6 text-white opacity-0 group-hover:opacity-100 transition-opacity" />
              </div>
              <span className="absolute top-2 left-2 px-1.5 py-0.5 text-xs bg-black/60 rounded text-white">
                #{i + 1}
              </span>
            </div>
          ))}
        </div>
        <button onClick={() => setShowGallery(false)}
                className="mt-3 text-xs text-gray-500 hover:text-gray-300 transition-colors">
          取消选择
        </button>
        {selecting && (
          <div className="flex items-center gap-2 mt-2 text-xs text-blue-400">
            <Loader2 className="w-3 h-3 animate-spin" /> 正在处理...
          </div>
        )}
      </div>
    );
  }

  // --- Full character card with all sections ---
  return (
    <div className="bg-gray-800 rounded-lg border border-gray-700 overflow-hidden">
      <audio ref={audioRef} className="hidden" onEnded={() => setPreviewPlaying(false)} />

      {/* Status bar */}
      <div className={`flex items-center gap-2 px-3 py-2 ${si.bg}`}>
        <StatusIcon className={`w-4 h-4 ${si.color} ${status.includes('GENERATING') ? 'animate-spin' : ''}`} />
        <span className={`text-xs ${si.color}`}>{si.label}</span>
      </div>

      {/* Images section */}
      <div className="p-3 space-y-3">
        {/* Portrait + Three-view row */}
        <div className="flex gap-3">
          {/* Portrait */}
          <div className="w-24">
            <p className="text-xs text-gray-500 mb-1">正面立绘</p>
            {portraitUrl ? (
              <img src={portraitUrl} alt={char.name}
                   className="w-full aspect-[3/4] object-cover rounded-lg bg-gray-700 cursor-pointer"
                   onClick={() => window.open(portraitUrl, '_blank')} />
            ) : (
              <div className="w-full aspect-[3/4] bg-gray-700 rounded-lg flex items-center justify-center">
                <Image className="w-6 h-6 text-gray-600" />
              </div>
            )}
          </div>

          {/* Three-view */}
          <div className="flex-1">
            <div className="flex items-center justify-between mb-1">
              <p className="text-xs text-gray-500">三视图</p>
              {threeViewUrl && (
                <div className="flex gap-1">
                  <button onClick={() => window.open(threeViewUrl, '_blank')}
                          className="p-1 text-xs text-gray-400 hover:text-white hover:bg-gray-700 rounded transition-colors"
                          title="查看大图">
                    <Eye className="w-3 h-3" />
                  </button>
                  {status === 'COMPLETED' && (
                    <button onClick={handleRegenerateThreeView} disabled={threeViewLoading}
                            className="p-1 text-xs text-gray-400 hover:text-yellow-400 hover:bg-gray-700 rounded transition-colors"
                            title="重新生成三视图">
                      <RefreshCw className={`w-3 h-3 ${threeViewLoading ? 'animate-spin' : ''}`} />
                    </button>
                  )}
                </div>
              )}
            </div>
            {threeViewUrl ? (
              <img src={threeViewUrl} alt={`${char.name} 三视图`}
                   className="w-full aspect-[16/9] object-cover rounded-lg bg-gray-700 cursor-pointer"
                   onClick={() => window.open(threeViewUrl, '_blank')} />
            ) : (
              <div className="w-full aspect-[16/9] bg-gray-700 rounded-lg flex items-center justify-center">
                {(status === 'THREEVIEW_GENERATING' || status === 'EXPRESSIONS_GENERATING') ? (
                  <Loader2 className="w-5 h-5 animate-spin text-purple-400" />
                ) : (
                  <span className="text-xs text-gray-600">
                    {status === 'CANDIDATES_READY' ? '请先选择立绘' : '等待生成'}
                  </span>
                )}
              </div>
            )}
          </div>
        </div>

        {/* Candidate selection prompt / gallery launcher */}
        {status === 'CANDIDATES_READY' && candidates.length > 0 && (
          <div>
            <button onClick={() => setShowGallery(true)}
                    className="w-full py-2 bg-yellow-600/20 hover:bg-yellow-600/30 border border-yellow-600/50 rounded-lg text-xs text-yellow-400 transition-colors">
              <Image className="w-3 h-3 inline mr-1" />
              查看候选立绘 ({candidates.length}张) - 点击选择最佳
            </button>
            <div className="flex gap-2 mt-2">
              {candidates.map((c, i) => (
                <img key={i} src={c.localUrl || c.ossUrl || c.url} alt={`候选${i + 1}`}
                     className="w-14 h-18 object-cover rounded bg-gray-700 cursor-pointer hover:ring-2 ring-yellow-400 transition-all"
                     onClick={() => setShowGallery(true)} />
              ))}
            </div>
          </div>
        )}

        {/* Character info */}
        <div>
          <div className="flex items-center justify-between">
            <div className="font-medium text-white">{char.name}</div>
            <button onClick={() => onViewPrompt?.(char.name)}
                    className="inline-flex items-center gap-1 px-2 py-0.5 text-xs bg-gray-700 hover:bg-gray-600 rounded transition-colors">
              <Eye className="w-3 h-3" /> 提示词
            </button>
          </div>
          <div className="text-xs text-gray-400 mt-1">
            {char.role} · {char.gender} · {char.ageRange}
          </div>
          <div className="text-xs text-gray-300 mt-1 line-clamp-2">{char.personality}</div>
        </div>

        {/* TTS Voice Selector + Preview */}
        {voiceList && voiceList.length > 0 && (
          <div className="flex items-center gap-2">
            <span className="text-xs text-gray-500">语音:</span>
            <select
              value={char.voiceConfig?.voice || 'zh-CN-XiaoxiaoNeural'}
              onChange={(e) => onVoiceChange?.(char.id, e.target.value)}
              className="flex-1 px-2 py-1 text-xs bg-gray-700 border border-gray-600 rounded text-gray-300 focus:outline-none focus:border-blue-500"
            >
              {voiceList.map((v) => (
                <option key={v.name} value={v.name}>
                  {v.name.replace('zh-CN-', '')} ({v.gender === 'Male' ? '男' : '女'})
                </option>
              ))}
            </select>
            <button
              onClick={handlePreviewVoice}
              disabled={previewPlaying}
              className="inline-flex items-center gap-1 px-2 py-1 text-xs bg-blue-600 hover:bg-blue-700 disabled:opacity-50 rounded transition-colors shrink-0"
            >
              {previewPlaying ? (
                <Loader2 className="w-3 h-3 animate-spin" />
              ) : (
                <Volume2 className="w-3 h-3" />
              )}
              试听
            </button>
          </div>
        )}

        {/* Expressions section */}
        {expressions.length > 0 && (
          <div>
            <button
              onClick={() => setShowExpressions(!showExpressions)}
              className="flex items-center justify-between w-full text-xs text-gray-400 hover:text-gray-300 transition-colors"
            >
              <span>
                表情 · {expressions.length}种
                {status === 'EXPRESSIONS_READY' && (
                  <span className="text-yellow-400 ml-1">(待选择)</span>
                )}
                {status === 'COMPLETED' && (
                  <span className="text-green-400 ml-1">(已确认)</span>
                )}
              </span>
              <span className="flex items-center gap-2">
                {status === 'COMPLETED' && (
                  <button onClick={(e) => { e.stopPropagation(); handleRegenerateExpressions(); }}
                          disabled={exprRegenerating}
                          className="px-2 py-0.5 bg-gray-700 hover:bg-gray-600 rounded text-xs"
                          title="重新生成所有表情">
                    <RefreshCw className={`w-3 h-3 ${exprRegenerating ? 'animate-spin' : ''}`} />
                  </button>
                )}
                {showExpressions ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
              </span>
            </button>

            {showExpressions && (
              <div className="mt-2 space-y-3">
                {expressions.map((expr) => {
                  const emotionType = expr.type;
                  const exprCandidates = expr.candidates || [];
                  const selectedIdx = expr.selectedIndex;
                  const selectedUrl = selectedIdx != null && exprCandidates[selectedIdx]
                    ? (exprCandidates[selectedIdx].ossUrl || exprCandidates[selectedIdx].localUrl || exprCandidates[selectedIdx].imageUrl)
                    : null;

                  return (
                    <div key={emotionType} className="bg-gray-750 rounded-lg p-2">
                      <div className="flex items-center justify-between mb-1">
                        <span className="text-xs text-gray-400 capitalize">{emotionType}</span>
                        {selectedUrl && (
                          <span className="text-xs text-green-400">已选 #{selectedIdx + 1}</span>
                        )}
                      </div>

                      {/* Show selected or candidates */}
                      {exprCandidates.length > 0 ? (
                        <div className="flex gap-1.5">
                          {exprCandidates.map((c, i) => {
                            const isSelected = selectedIdx === i;
                            const url = c.ossUrl || c.localUrl || c.imageUrl;
                            return (
                              <div key={i} className="relative">
                                <img
                                  src={url}
                                  alt={`${emotionType} ${i + 1}`}
                                  onClick={() => {
                                    if (status === 'EXPRESSIONS_READY' || status === 'COMPLETED') {
                                      handleSelectExpression(emotionType, i);
                                    }
                                  }}
                                  className={`w-10 h-10 object-cover rounded cursor-pointer transition-all ${
                                    isSelected ? 'ring-2 ring-green-400 opacity-100' : 'hover:ring-1 ring-gray-500 opacity-70 hover:opacity-100'
                                  }`}
                                />
                                {isSelected && (
                                  <Check className="absolute -top-1 -right-1 w-3 h-3 bg-green-500 rounded-full p-0.5 text-white" />
                                )}
                                {selectingExpr === emotionType && (
                                  <div className="absolute inset-0 flex items-center justify-center bg-black/50 rounded">
                                    <Loader2 className="w-3 h-3 animate-spin text-white" />
                                  </div>
                                )}
                              </div>
                            );
                          })}
                        </div>
                      ) : selectedUrl ? (
                        <img src={selectedUrl} alt={emotionType}
                             className="w-10 h-10 object-cover rounded" />
                      ) : (
                        <span className="text-xs text-gray-600">等待生成</span>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* Appearance attributes */}
        {char.appearance && (
          <div className="flex flex-wrap gap-1">
            {Object.entries(char.appearance)
              .filter(([k]) => !['portraitUrl', 'portraitLocalUrl', 'threeViewUrl', 'threeViewLocalUrl', 'referenceImageUrl'].includes(k))
              .map(([k, v]) => (
                <span key={k} className="text-xs px-2 py-0.5 bg-gray-700 rounded text-gray-400">
                  {k}: {typeof v === 'string' ? v : JSON.stringify(v)}
                </span>
              ))}
          </div>
        )}
      </div>
    </div>
  );
}

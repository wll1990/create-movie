import { useState, useEffect, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import { Play, Upload, Loader2, ChevronDown, ChevronUp, Info, Music, Copy } from 'lucide-react';
import { workflowApi } from '../api/workflowApi.js';
import WorkflowStepper from '../components/WorkflowStepper/WorkflowStepper.jsx';
import { projectApi } from '../api/projectApi.js';
import { scriptApi } from '../api/scriptApi.js';
import { characterApi } from '../api/characterApi.js';
import { storyboardApi } from '../api/storyboardApi.js';
import { compositionApi, materialApi, ttsApi } from '../api/compositionApi.js';
import client from '../api/client.js';
import { episodeApi } from '../api/episodeApi.js';
import { analysisApi } from '../api/analysisApi.js';
import FlowPanel from '../components/FlowPanel/FlowPanel.jsx';
import EpisodeSelector from '../components/EpisodeSelector/EpisodeSelector.jsx';
import ScriptEditor from '../components/ScriptEditor/ScriptEditor.jsx';
import StoryboardEditor from '../components/StoryboardEditor/StoryboardEditor.jsx';
import VideoPreview from '../components/VideoPreview/VideoPreview.jsx';
import CharacterCard from '../components/CharacterCard/CharacterCard.jsx';
import ClipGenerationPanel from '../components/ClipGeneration/ClipGenerationPanel.jsx';

export default function ProjectPage() {
  const { projectId } = useParams();
  const [project, setProject] = useState(null);
  const [script, setScript] = useState(null);
  const [characters, setCharacters] = useState([]);
  const [storyboard, setStoryboard] = useState(null);
  const [composition, setComposition] = useState(null);
  const [clipProgress, setClipProgress] = useState(null);
  const [gene, setGene] = useState(null);
  const [episodes, setEpisodes] = useState([]);
  const [currentEpisodeId, setCurrentEpisodeId] = useState(null);
  const [loading, setLoading] = useState({});
  const [bgmList, setBgmList] = useState([]);
  const [selectedBgmId, setSelectedBgmId] = useState('');
  const [voiceList, setVoiceList] = useState([]);
  const [showGuide, setShowGuide] = useState(
    () => !localStorage.getItem('makemovie-guide-dismissed')
  );
  const [showScriptDialog, setShowScriptDialog] = useState(false);
  const [scriptTheme, setScriptTheme] = useState('');
  const [scriptDuration, setScriptDuration] = useState(45);
  const [charPrompts, setCharPrompts] = useState([]);
  const [showCharPrompt, setShowCharPrompt] = useState(null);
  const [editingCharPrompt, setEditingCharPrompt] = useState('');
  const [regeneratingChar, setRegeneratingChar] = useState(null);
  const [charProgress, setCharProgress] = useState(null);
  const [showAutoNext, setShowAutoNext] = useState(false);

  const currentEpisode = episodes.find(e => e.id === currentEpisodeId) || episodes[0];
  const episodeLabel = currentEpisode
    ? `第${currentEpisode.episodeNumber}集`
    : '';

  const loadProject = useCallback(async () => {
    try {
      const data = await projectApi.get(projectId);
      setProject(data);
    } catch (e) {
      console.error('Failed to load project:', e);
    }
  }, [projectId]);

  useEffect(() => {
    loadProject();
    loadEpisodes();
  }, [loadProject]);

  const loadEpisodes = async () => {
    try {
      const eps = await episodeApi.list(projectId);
      setEpisodes(eps || []);
      if (eps && eps.length > 0 && !currentEpisodeId) {
        setCurrentEpisodeId(eps[0].id);
      }
    } catch (e) {
      console.error('Failed to load episodes:', e);
    }
  };

  const handleEpisodeChange = (episodeId) => {
    setCurrentEpisodeId(episodeId);
    setScript(null);
    setCharacters([]);
    setStoryboard(null);
    setComposition(null);
    setClipProgress(null);
  };

  // Load existing data when project loads
  useEffect(() => {
    if (!project) return;
    const loadData = async () => {
      const progress = project.progress?.steps || {};
      try {
        if (progress.SCRIPT_CREATION?.status === 'COMPLETED') {
          const s = await scriptApi.get(projectId);
          setScript(s);
        }
      } catch {}
      try {
        if (progress.CHARACTER_DESIGN?.status === 'COMPLETED') {
          const c = await characterApi.get(projectId);
          setCharacters(c);
        }
      } catch {}
      try {
        if (progress.STORYBOARD_DESIGN?.status === 'COMPLETED') {
          const sb = await storyboardApi.get(projectId);
          setStoryboard(sb);
        }
      } catch {}
      try {
        if (progress.FINAL_COMPOSITION?.status === 'COMPLETED') {
          const comp = await compositionApi.get(projectId);
          setComposition(comp);
        }
      } catch {}
      try {
        if (progress.CLIP_GENERATION?.status === 'RUNNING' ||
            progress.CLIP_GENERATION?.status === 'COMPLETED') {
          const cp = await compositionApi.getClipProgress(projectId);
          setClipProgress(cp);
        }
      } catch {}
      try {
        if (project.mode !== 'CREATION') {
          const g = await analysisApi.getGene(projectId);
          setGene(g);
        }
      } catch {}
    };
    loadData();
  }, [project?.id]);

  // Load BGM list and voice list (project-independent, load once)
  useEffect(() => {
    materialApi.listBgm().then(setBgmList).catch(() => {});
    ttsApi.listVoices().then(setVoiceList).catch(() => {});
  }, []);

  // Sync selected BGM from composition
  useEffect(() => {
    if (composition?.bgmMaterialId) {
      setSelectedBgmId(composition.bgmMaterialId);
    }
  }, [composition?.bgmMaterialId]);

  const handleBgmChange = async (e) => {
    const bgmId = e.target.value;
    setSelectedBgmId(bgmId);
    if (composition?.id) {
      try {
        await compositionApi.setBgm(projectId, composition.id, bgmId);
      } catch (err) {
        console.error('Failed to set BGM:', err);
      }
    }
  };

  const handleVoiceChange = async (charId, voiceName) => {
    try {
      const config = { voice: voiceName, speed: 1.0, pitch: 0 };
      await client.put(`/projects/${projectId}/characters/${charId}/voice`, config);
      // Update local state
      setCharacters(prev => prev.map(c =>
        c.id === charId ? { ...c, voiceConfig: { ...c.voiceConfig, voice: voiceName } } : c
      ));
    } catch (err) {
      console.error('Failed to update voice:', err);
    }
  };

  const setLoadingStep = (step, val) =>
    setLoading((prev) => ({ ...prev, [step]: val }));

  const openScriptDialog = () => {
    setScriptTheme(project.theme || project.title || '');
    setScriptDuration(45);
    setShowScriptDialog(true);
  };

  const handleGenerateScript = async () => {
    if (!scriptTheme.trim()) {
      alert('请输入创作主题');
      return;
    }
    setShowScriptDialog(false);
    setLoadingStep('script', true);
    try {
      const s = await scriptApi.generate(projectId, {
        track: project.track || '都市甜宠',
        theme: scriptTheme.trim(),
        duration: scriptDuration,
      });
      setScript(s);
      await loadProject();
      // Check if auto-next is enabled
      if (localStorage.getItem('makemovie-auto-next') === 'true') {
        handleGenerateCharacters();
      } else {
        setShowAutoNext(true);
      }
    } catch (e) {
      alert('剧本生成失败: ' + (e.response?.data?.message || e.message));
    } finally {
      setLoadingStep('script', false);
    }
  };

  const handleUpdateScript = async (scriptId, data) => {
    try {
      const s = await scriptApi.update(projectId, data);
      setScript(s);
    } catch (e) {
      alert('保存失败: ' + (e.response?.data?.message || e.message));
    }
  };

  const handleGenerateCharacters = async () => {
    setLoadingStep('character', true);
    setCharProgress(null);
    // Start polling for progress
    const pollInterval = setInterval(async () => {
      try {
        const log = await workflowApi.getStepLog(projectId, 'CHARACTER_DESIGN');
        if (log?.outputData) {
          setCharProgress(log.outputData);
        }
      } catch {}
    }, 2000);
    try {
      const c = await characterApi.generate(projectId);
      setCharacters(c);
      await loadProject();
      clearInterval(pollInterval);
      // Show final progress
      try {
        const log = await workflowApi.getStepLog(projectId, 'CHARACTER_DESIGN');
        if (log?.outputData) setCharProgress(log.outputData);
      } catch {}
    } catch (e) {
      clearInterval(pollInterval);
      alert('人设生成失败: ' + (e.response?.data?.message || e.message));
    } finally {
      setLoadingStep('character', false);
    }
  };

  const handleGenerateStoryboard = async () => {
    setLoadingStep('storyboard', true);
    try {
      const sb = await storyboardApi.generate(projectId);
      setStoryboard(sb);
      await loadProject();
    } catch (e) {
      alert('分镜生成失败: ' + (e.response?.data?.message || e.message));
    } finally {
      setLoadingStep('storyboard', false);
    }
  };

  const handleAnalyzeVideo = async (file) => {
    setLoadingStep('analyze', true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const g = await analysisApi.analyze(projectId, formData);
      setGene(g);
      await loadProject();
    } catch (e) {
      alert('视频分析失败: ' + (e.response?.data?.message || e.message));
    } finally {
      setLoadingStep('analyze', false);
    }
  };

  const handleGenerateVoice = async () => {
    setLoadingStep('voice', true);
    try {
      await compositionApi.generateVoice(projectId);
      await loadProject();
    } catch (e) {
      alert('配音生成失败: ' + (e.response?.data?.message || e.message));
    } finally {
      setLoadingStep('voice', false);
    }
  };

  const handleInitClips = async () => {
    setLoadingStep('clips', true);
    try {
      await compositionApi.initClips(projectId);
      const cp = await compositionApi.getClipProgress(projectId);
      setClipProgress(cp);
      await loadProject();
    } catch (e) {
      alert('片段初始化失败: ' + (e.response?.data?.message || e.message));
    } finally {
      setLoadingStep('clips', false);
    }
  };

  const handleFinalComposition = async () => {
    setLoadingStep('compose', true);
    try {
      await compositionApi.submit(projectId);
      await loadProject();
    } catch (e) {
      alert('合成提交失败: ' + (e.response?.data?.message || e.message));
    } finally {
      setLoadingStep('compose', false);
    }
  };

  // WorkflowStepper action router — maps step key to handler
  const handleWorkflowAction = (stepKey) => {
    switch (stepKey) {
      case 'SCRIPT_CREATION': openScriptDialog(); break;
      case 'CHARACTER_DESIGN': handleGenerateCharacters(); break;
      case 'STORYBOARD_DESIGN': handleGenerateStoryboard(); break;
      case 'VOICE_GENERATION': handleGenerateVoice(); break;
      case 'CLIP_GENERATION': handleInitClips(); break;
      case 'FINAL_COMPOSITION': handleFinalComposition(); break;
    }
  };

  if (!project) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="w-6 h-6 animate-spin text-gray-500" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">{project.title}</h1>
          <p className="text-sm text-gray-500 mt-1">
            {project.track || '未指定赛道'} · {project.mode} · 共{episodes.length}集
          </p>
        </div>
        <div className="flex items-center gap-3">
          <EpisodeSelector
            projectId={projectId}
            currentEpisodeId={currentEpisodeId}
            onEpisodeChange={handleEpisodeChange}
          />
          <span className={`px-3 py-1 rounded-lg text-sm ${
            project.status === 'COMPLETED' ? 'bg-green-900/50 text-green-400' :
            project.status === 'PROCESSING' ? 'bg-blue-900/50 text-blue-400' :
            'bg-gray-700 text-gray-400'
          }`}>
            {project.status}
          </span>
        </div>
      </div>

      {/* Usage Guide Banner */}
      {showGuide && (
        <div className="bg-gradient-to-r from-blue-900/30 to-purple-900/30 border border-blue-800/50 rounded-xl p-4 relative">
          <button
            onClick={() => {
              setShowGuide(false);
              localStorage.setItem('makemovie-guide-dismissed', '1');
            }}
            className="absolute top-3 right-3 text-gray-500 hover:text-gray-300 transition-colors"
            title="关闭引导"
          >
            <ChevronUp className="w-4 h-4" />
          </button>
          <div className="flex items-start gap-3">
            <Info className="w-5 h-5 text-blue-400 shrink-0 mt-0.5" />
            <div>
              <h3 className="text-sm font-semibold text-blue-300 mb-2">如何使用项目工作台</h3>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-xs text-gray-400">
                <div>
                  <span className="text-white font-medium">① 左侧面板</span>
                  <br />查看 8 步创作流程进度，点击步骤查看详情。按顺序点击快捷操作按钮推进流程。
                </div>
                <div>
                  <span className="text-white font-medium">② 右侧编辑器</span>
                  <br />查看和编辑生成的剧本、角色、分镜。每步完成后可以在这里检查质量。
                </div>
                <div>
                  <span className="text-white font-medium">③ 关键步骤：视频片段生成</span>
                  <br />AI 逐帧生成动画，每帧你可以审核 prompt → 确认生成 → 预览 → 通过或重试。
                </div>
              </div>
              <button
                onClick={() => {
                  setShowGuide(false);
                  localStorage.setItem('makemovie-guide-dismissed', '1');
                }}
                className="mt-3 text-xs text-blue-400 hover:underline"
              >
                知道了，不再显示
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Workflow stepper + quick actions (dynamic, driven by backend API) */}
      <WorkflowStepper
        project={project}
        loading={loading}
        onAction={handleWorkflowAction}
      />

      {/* Two-column layout */}
      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        {/* Left: Flow Panel */}
        <div className="lg:col-span-1">
          <div className="space-y-4 sticky top-6">
            <FlowPanel project={project} episodeLabel={episodeLabel} />

            {/* Analysis mode: upload video */}
            {project.mode !== 'CREATION' && (
              <div className="bg-gray-900 border border-gray-800 rounded-xl p-4">
                <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-3">
                  视频分析
                </h3>
                <label className="block">
                  <input
                    type="file"
                    accept="video/*"
                    onChange={(e) => e.target.files?.[0] && handleAnalyzeVideo(e.target.files[0])}
                    className="hidden"
                  />
                  <div className="flex items-center justify-center gap-2 px-3 py-2.5 bg-gray-800 hover:bg-gray-700 border border-dashed border-gray-600 rounded-lg cursor-pointer transition-colors text-xs text-gray-400">
                    {loading.analyze ? (
                      <Loader2 className="w-3 h-3 animate-spin" />
                    ) : (
                      <Upload className="w-3 h-3" />
                    )}
                    上传视频分析
                  </div>
                </label>
                {gene && (
                  <div className="mt-3 p-2 bg-gray-800 rounded-lg">
                    <p className="text-xs text-gray-500">
                      赛道: {gene.track} · 已提取{Object.keys(gene.contentGene || {}).length}个内容特征
                    </p>
                  </div>
                )}
              </div>
            )}

            {/* BGM Selector */}
            {bgmList.length > 0 && (
              <div className="bg-gray-900 border border-gray-800 rounded-xl p-4">
                <div className="flex items-center gap-2">
                  <Music className="w-4 h-4 text-gray-500 shrink-0" />
                  <select
                    value={selectedBgmId}
                    onChange={handleBgmChange}
                    className="flex-1 px-2 py-1.5 text-sm bg-gray-800 border border-gray-700 rounded-lg text-gray-300 focus:outline-none focus:border-blue-500"
                  >
                    <option value="">无 BGM</option>
                    {bgmList.map((bgm) => (
                      <option key={bgm.id} value={bgm.id}>
                        {bgm.name}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
            )}

            {/* Clip generation panel (shown during step 6) */}
            {clipProgress && clipProgress.frames && clipProgress.frames.length > 0 && (
              <ClipGenerationPanel
                projectId={projectId}
                clipProgress={clipProgress}
                onRefresh={async () => {
                  const cp = await compositionApi.getClipProgress(projectId);
                  setClipProgress(cp);
                  await loadProject();
                }}
              />
            )}
          </div>
        </div>

        {/* Right: Content area */}
        <div className="lg:col-span-3 space-y-4">
          <ScriptEditor
            script={script}
            onGenerate={openScriptDialog}
            onUpdate={handleUpdateScript}
            loading={loading.script}
            projectId={projectId}
          />

          {(characters.length > 0 || loading.character) && (
            <div className="bg-gray-900 border border-gray-800 rounded-xl p-4">
              <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-3">
                角色人设 · {characters.length || (charProgress?.totalCharacters || 0)}人
              </h3>
              {/* Character generation progress */}
              {loading.character && charProgress && (
                <div className="mb-3 p-3 bg-blue-900/20 border border-blue-800/50 rounded-lg">
                  <div className="flex items-center gap-2 text-sm text-blue-300 mb-1">
                    <Loader2 className="w-4 h-4 animate-spin" />
                    正在设计角色 ({charProgress.currentIndex}/{charProgress.totalCharacters})
                  </div>
                  <div className="w-full h-2 bg-gray-800 rounded-full mt-2">
                    <div className="h-full bg-blue-500 rounded-full transition-all"
                         style={{ width: `${(charProgress.currentIndex / charProgress.totalCharacters) * 100}%` }} />
                  </div>
                  <p className="text-xs text-blue-400 mt-1">
                    当前: {charProgress.currentCharacter}
                  </p>
                  {charProgress.completedDetails?.length > 0 && (
                    <div className="mt-2 text-xs text-gray-400 space-y-0.5">
                      {charProgress.completedDetails.map((d, i) => (
                        <div key={i}>{d.name} 完成 ({d.elapsedMs}ms)</div>
                      ))}
                    </div>
                  )}
                </div>
              )}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {characters.map((char) => (
                  <CharacterCard
                    key={char.id}
                    char={char}
                    projectId={projectId}
                    voiceList={voiceList}
                    onVoiceChange={handleVoiceChange}
                    onViewPrompt={async (charName) => {
                      try {
                        const data = await client.get(`/projects/${projectId}/characters/prompts`);
                        setCharPrompts(data.data || []);
                        const entry = (data.data || []).find(p => p.name === charName);
                        setShowCharPrompt(charName);
                        setEditingCharPrompt(entry?.prompt || '');
                      } catch { setShowCharPrompt(charName); }
                    }}
                  />
                ))}
              </div>
            </div>
          )}

          {/* Character Prompt Modal */}
          {showCharPrompt && (
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60"
                 onClick={() => { setShowCharPrompt(null); setEditingCharPrompt(''); }}>
              <div className="bg-gray-900 border border-gray-700 rounded-xl w-full max-w-2xl mx-4 max-h-[85vh] overflow-y-auto"
                   onClick={(e) => e.stopPropagation()}>
                <div className="flex items-center justify-between p-4 border-b border-gray-700">
                  <h3 className="text-lg font-semibold">
                    角色提示词 · {showCharPrompt}
                  </h3>
                  <button onClick={() => { setShowCharPrompt(null); setEditingCharPrompt(''); }}
                          className="p-1 hover:bg-gray-700 rounded-lg transition-colors">
                    <span className="text-gray-400 text-lg">&times;</span>
                  </button>
                </div>
                <div className="p-4 space-y-3">
                  <p className="text-xs text-gray-500">
                    编辑提示词后点"重新生成"可单独重新生成该角色
                  </p>
                  <textarea
                    value={editingCharPrompt}
                    onChange={(e) => setEditingCharPrompt(e.target.value)}
                    className="w-full h-64 bg-gray-800 text-gray-200 text-sm font-mono rounded-lg p-3 border border-gray-700 focus:border-blue-500 focus:outline-none resize-y"
                  />
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => navigator.clipboard.writeText(editingCharPrompt)}
                      className="inline-flex items-center gap-1 px-3 py-1.5 text-xs bg-gray-700 hover:bg-gray-600 rounded-lg transition-colors"
                    >
                      <Copy className="w-3 h-3" /> 复制
                    </button>
                    <button
                      onClick={async () => {
                        const char = characters.find(c => c.name === showCharPrompt);
                        if (!char) return;
                        setRegeneratingChar(char.id);
                        try {
                          await client.put(
                            `/projects/${projectId}/characters/${char.id}/regenerate`,
                            { prompt: editingCharPrompt }
                          );
                          const updated = await characterApi.get(projectId);
                          setCharacters(updated);
                          setShowCharPrompt(null);
                          setEditingCharPrompt('');
                        } catch (e) {
                          alert('重新生成失败: ' + (e.response?.data?.message || e.message));
                        } finally {
                          setRegeneratingChar(null);
                        }
                      }}
                      disabled={regeneratingChar}
                      className="inline-flex items-center gap-1 px-3 py-1.5 text-xs bg-blue-600 hover:bg-blue-700 disabled:opacity-50 rounded-lg transition-colors"
                    >
                      {regeneratingChar ? '生成中...' : '重新生成'}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          )}

          <StoryboardEditor
            storyboard={storyboard}
            onGenerate={handleGenerateStoryboard}
            loading={loading.storyboard}
            projectId={projectId}
            characters={characters}
          />

          <VideoPreview
            projectId={projectId}
            composition={composition}
          />
        </div>
      </div>

      {/* Auto-next-step Dialog */}
      {showAutoNext && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60"
             onClick={() => setShowAutoNext(false)}>
          <div className="bg-gray-900 border border-gray-700 rounded-xl w-full max-w-md mx-4"
               onClick={(e) => e.stopPropagation()}>
            <div className="p-4 border-b border-gray-700">
              <h3 className="text-lg font-semibold">剧本已生成完成！</h3>
              <p className="text-xs text-gray-500 mt-1">
                是否继续进入下一步：人设设计？
              </p>
            </div>
            <div className="p-4 space-y-3">
              <div className="flex gap-3">
                <button onClick={() => {
                  setShowAutoNext(false);
                  handleGenerateCharacters();
                }}
                        className="flex-1 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg text-sm transition-colors">
                  继续生成人设
                </button>
                <button onClick={() => setShowAutoNext(false)}
                        className="flex-1 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm transition-colors">
                  稍后再说
                </button>
              </div>
              <label className="flex items-center gap-2 text-xs text-gray-500 cursor-pointer">
                <input type="checkbox"
                       onChange={(e) => {
                         if (e.target.checked) {
                           localStorage.setItem('makemovie-auto-next', 'true');
                         } else {
                           localStorage.removeItem('makemovie-auto-next');
                         }
                       }}
                       className="rounded bg-gray-700 border-gray-600" />
                以后不再提示，自动继续下一步
              </label>
            </div>
          </div>
        </div>
      )}

      {/* Script Generation Dialog */}
      {showScriptDialog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60"
             onClick={() => setShowScriptDialog(false)}>
          <div className="bg-gray-900 border border-gray-700 rounded-xl w-full max-w-lg mx-4"
               onClick={(e) => e.stopPropagation()}>
            <div className="p-4 border-b border-gray-700">
              <h3 className="text-lg font-semibold">生成剧本</h3>
              <p className="text-xs text-gray-500 mt-1">
                输入创作主题，AI 将使用专业编剧方法论生成剧本
              </p>
            </div>
            <div className="p-4 space-y-4">
              <div>
                <label className="block text-sm text-gray-400 mb-1">赛道</label>
                <input type="text" value={project?.track || ''}
                       className="w-full bg-gray-800 border border-gray-700 rounded-lg px-3 py-2 text-gray-300 text-sm"
                       disabled />
              </div>
              <div>
                <label className="block text-sm text-gray-400 mb-1">
                  创作主题 <span className="text-red-400">*</span>
                </label>
                <textarea
                  value={scriptTheme}
                  onChange={(e) => setScriptTheme(e.target.value)}
                  placeholder="详细描述你想创作的故事主题，越具体越好。例如：霸道总裁在咖啡店遇到打工女孩，两人因误会结缘，经历身份反转后终成眷属..."
                  rows={3}
                  className="w-full bg-gray-800 border border-gray-700 rounded-lg px-3 py-2 text-white text-sm placeholder-gray-500 focus:outline-none focus:border-blue-500 resize-y"
                />
                <p className="text-xs text-gray-600 mt-1">
                  主题越具体，生成的剧本越贴合你的预期。AI 会基于此构建人物、情节和台词。
                </p>
              </div>
              <div>
                <label className="block text-sm text-gray-400 mb-1">目标时长（秒）</label>
                <input
                  type="number"
                  value={scriptDuration}
                  onChange={(e) => setScriptDuration(Number(e.target.value))}
                  min={15} max={120} step={5}
                  className="w-32 bg-gray-800 border border-gray-700 rounded-lg px-3 py-2 text-white text-sm focus:outline-none focus:border-blue-500"
                />
              </div>
            </div>
            <div className="flex items-center justify-end gap-3 p-4 border-t border-gray-700">
              <button onClick={() => setShowScriptDialog(false)}
                      className="px-4 py-2 text-sm text-gray-400 hover:bg-gray-800 rounded-lg transition-colors">
                取消
              </button>
              <button onClick={handleGenerateScript}
                      disabled={!scriptTheme.trim()}
                      className="px-4 py-2 text-sm bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed rounded-lg transition-colors">
                开始生成
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

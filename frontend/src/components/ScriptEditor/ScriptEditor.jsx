import { useState } from 'react';
import { Sparkles, Save, Loader2, Eye } from 'lucide-react';
import { workflowApi } from '../../api/workflowApi.js';

export default function ScriptEditor({ script, onGenerate, onUpdate, loading, projectId }) {
  const [editing, setEditing] = useState(false);
  const [content, setContent] = useState(null);
  const [showPrompt, setShowPrompt] = useState(false);
  const [promptText, setPromptText] = useState('');
  const [promptLoading, setPromptLoading] = useState(false);

  if (!script) {
    return (
      <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 text-center">
        <p className="text-gray-500 mb-4">尚未生成剧本</p>
        <button
          onClick={onGenerate}
          disabled={loading}
          className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 rounded-lg text-sm transition-colors"
        >
          {loading ? (
            <Loader2 className="w-4 h-4 animate-spin" />
          ) : (
            <Sparkles className="w-4 h-4" />
          )}
          生成剧本
        </button>
      </div>
    );
  }

  const handleEdit = () => {
    setContent(JSON.stringify(script.content, null, 2));
    setEditing(true);
  };

  const handleSave = async () => {
    try {
      const parsed = JSON.parse(content);
      await onUpdate(script.id, {
        title: script.title,
        content: parsed,
      });
      setEditing(false);
    } catch {
      alert('JSON格式不正确');
    }
  };

  const handleViewPrompt = async () => {
    setPromptLoading(true);
    setShowPrompt(true);
    try {
      const log = await workflowApi.getStepLog(projectId, 'SCRIPT_CREATION');
      setPromptText(log?.prompt || '(未找到提示词记录)');
    } catch {
      setPromptText('(获取提示词失败)');
    } finally {
      setPromptLoading(false);
    }
  };

  return (
    <div className="bg-gray-900 border border-gray-800 rounded-xl p-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider">
          剧本
        </h3>
        <div className="flex items-center gap-2">
          {!editing && projectId && (
            <button onClick={handleViewPrompt}
                    className="inline-flex items-center gap-1 px-3 py-1.5 text-xs bg-gray-700 hover:bg-gray-600 rounded-lg transition-colors">
              <Eye className="w-3 h-3" /> 查看提示词
            </button>
          )}
          {!editing && (
            <button onClick={handleEdit}
                    className="inline-flex items-center gap-1 px-3 py-1.5 text-xs bg-gray-700 hover:bg-gray-600 rounded-lg transition-colors">
              <Save className="w-3 h-3" /> 编辑
            </button>
          )}
          <button onClick={onGenerate} disabled={loading}
                  className="inline-flex items-center gap-1 px-3 py-1.5 text-xs bg-blue-600 hover:bg-blue-700 disabled:opacity-50 rounded-lg transition-colors">
            {loading ? <Loader2 className="w-3 h-3 animate-spin" /> : <Sparkles className="w-3 h-3" />}
            重新生成
          </button>
        </div>
      </div>

      {editing ? (
        <div className="space-y-3">
          <textarea
            className="w-full h-64 bg-gray-800 text-gray-200 text-sm font-mono rounded-lg p-3 border border-gray-700 focus:border-blue-500 focus:outline-none resize-y"
            value={content}
            onChange={(e) => setContent(e.target.value)}
          />
          <div className="flex gap-2">
            <button onClick={handleSave}
                    className="px-4 py-1.5 text-sm bg-green-600 hover:bg-green-700 rounded-lg transition-colors">
              保存
            </button>
            <button onClick={() => setEditing(false)}
                    className="px-4 py-1.5 text-sm bg-gray-700 hover:bg-gray-600 rounded-lg transition-colors">
              取消
            </button>
          </div>
        </div>
      ) : (
        <div>
          <h4 className="text-lg font-medium text-white mb-2">
            {script.title}
            <span className="text-xs text-gray-500 ml-2">v{script.version}</span>
          </h4>
          <p className="text-sm text-gray-400 mb-3">
            {script.track} · {script.duration}秒
          </p>
          <div className="space-y-2">
            {(script.content?.scenes || []).map((scene, i) => (
              <div key={i} className="bg-gray-800 rounded-lg p-3">
                <div className="text-xs text-gray-500 mb-1">
                  场景{i + 1}: {scene.location} · {scene.timeOfDay || '未指定'}
                </div>
                <p className="text-sm text-gray-400 mb-2">{scene.summary}</p>
                {(scene.dialogues || []).map((d, j) => (
                  <div key={j} className="flex items-start gap-2 py-1 border-t border-gray-700/50">
                    <span className="text-xs font-medium text-blue-400 shrink-0 w-14">
                      {d.characterName}
                    </span>
                    <span className="text-xs text-gray-300">"{d.text}"</span>
                    <span className="text-xs text-gray-600">{d.emotion}</span>
                  </div>
                ))}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Prompt preview modal */}
      {showPrompt && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60"
             onClick={() => setShowPrompt(false)}>
          <div className="bg-gray-900 border border-gray-700 rounded-xl w-full max-w-3xl mx-4 max-h-[80vh] overflow-y-auto"
               onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between p-4 border-b border-gray-700">
              <h3 className="text-lg font-semibold">生成提示词</h3>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => {
                    navigator.clipboard.writeText(promptText);
                  }}
                  className="px-3 py-1.5 text-xs bg-gray-700 hover:bg-gray-600 rounded-lg transition-colors"
                >
                  复制
                </button>
                <button onClick={() => setShowPrompt(false)}
                        className="p-1 hover:bg-gray-700 rounded-lg transition-colors">
                  <span className="text-gray-400 text-lg">&times;</span>
                </button>
              </div>
            </div>
            <div className="p-4">
              {promptLoading ? (
                <div className="flex items-center gap-2 text-gray-400">
                  <Loader2 className="w-4 h-4 animate-spin" /> 加载中...
                </div>
              ) : (
                <pre className="text-xs text-gray-300 bg-gray-800 rounded-lg p-4 whitespace-pre-wrap max-h-[60vh] overflow-y-auto">
                  {promptText}
                </pre>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

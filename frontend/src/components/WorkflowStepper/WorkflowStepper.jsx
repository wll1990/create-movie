import { useState, useEffect } from 'react';
import { Check, Loader2, XCircle, Circle, Sparkles, User, Layout, Volume, Clapperboard, Film } from 'lucide-react';
import client from '../../api/client.js';

const ICON_MAP = {
  sparkles: Sparkles,
  user: User,
  layout: Layout,
  volume: Volume,
  clapperboard: Clapperboard,
  film: Film,
};

export default function WorkflowStepper({ project, loading, onAction }) {
  const [definitions, setDefinitions] = useState([]);

  useEffect(() => {
    client.get('/workflow/definitions').then(res => {
      setDefinitions(res.data || []);
    }).catch(() => {});
  }, []);

  if (!definitions.length) return null;

  const steps = project?.progress?.steps || {};

  return (
    <div className="space-y-4">
      {/* Horizontal step indicator */}
      <div className="bg-gray-900 border border-gray-800 rounded-xl p-4 overflow-x-auto">
        <div className="flex items-center gap-1 min-w-max">
          {definitions.map((def, i) => {
            const key = def.key;
            const info = steps[key];
            const status = info?.status || 'PENDING';
            const isCompleted = status === 'COMPLETED';
            const isRunning = status === 'RUNNING';
            const isFailed = status === 'FAILED';

            return (
              <div key={key} className="flex items-center gap-1">
                <div className={`flex items-center gap-2 px-3 py-2 rounded-lg text-xs whitespace-nowrap transition-all ${
                  isCompleted ? 'bg-green-900/30 border border-green-700/50 text-green-300' :
                  isRunning ? 'bg-blue-900/30 border border-blue-500 text-blue-300 ring-1 ring-blue-500/50' :
                  isFailed ? 'bg-red-900/30 border border-red-700/50 text-red-300' :
                  'bg-gray-800/50 border border-gray-700/30 text-gray-500'
                }`}>
                  <span className="shrink-0">
                    {isCompleted ? <Check className="w-4 h-4 text-green-400" /> :
                     isRunning ? <Loader2 className="w-4 h-4 text-blue-400 animate-spin" /> :
                     isFailed ? <XCircle className="w-4 h-4 text-red-400" /> :
                     <Circle className="w-4 h-4" />}
                  </span>
                  <span>{def.label}</span>
                </div>
                {i < definitions.length - 1 && (
                  <div className={`w-4 h-px ${isCompleted ? 'bg-green-700' : 'bg-gray-700'}`} />
                )}
              </div>
            );
          })}
        </div>
      </div>

      {/* Quick action buttons — generated from definitions */}
      <div className="bg-gray-900 border border-gray-800 rounded-xl p-4">
        <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-3">
          快捷操作
        </h3>
        <div className="space-y-2">
          {definitions.map((def) => {
            const actions = def.actions || [];
            if (!actions.length) return null;

            return actions.map((action) => {
              const requires = def.requires || [];
              // Check if this action's step is available
              const allRequirementsMet = requires.length === 0 || requires.every(reqKey => {
                const reqInfo = steps[reqKey];
                return reqInfo?.status === 'COMPLETED';
              });
              const isDisabled = !allRequirementsMet || loading[def.key];

              const Icon = ICON_MAP[action.icon] || Sparkles;

              return (
                <button
                  key={action.key}
                  onClick={() => onAction(def.key)}
                  disabled={isDisabled}
                  title={action.label}
                  className="w-full text-left px-3 py-2 text-sm bg-gray-800 hover:bg-gray-700 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  <span className="inline-flex items-center gap-2">
                    {loading[def.key] ? (
                      <Loader2 className="w-4 h-4 animate-spin" />
                    ) : (
                      <Icon className="w-4 h-4" />
                    )}
                    {loading[def.key] ? '生成中...' : action.label}
                  </span>
                </button>
              );
            });
          })}
        </div>
      </div>
    </div>
  );
}

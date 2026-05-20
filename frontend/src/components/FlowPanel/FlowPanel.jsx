import { useState, useEffect } from 'react';
import FlowStep from './FlowStep.jsx';
import LogDetailModal from './LogDetailModal.jsx';
import { workflowApi } from '../../api/workflowApi.js';

const STEP_ORDER = [
  'TOPIC_DESIGN', 'SCRIPT_CREATION', 'CHARACTER_DESIGN', 'STORYBOARD_DESIGN',
  'VOICE_GENERATION', 'CLIP_GENERATION', 'FINAL_COMPOSITION', 'COPYWRITING',
];

export default function FlowPanel({ project, episodeLabel }) {
  const [selectedStep, setSelectedStep] = useState(null);
  const [stepDetail, setStepDetail] = useState(null);
  const [loading, setLoading] = useState(false);

  const progress = project?.progress;
  const steps = progress?.steps || {};

  const handleStepClick = async (step) => {
    setSelectedStep(step);
    setLoading(true);
    try {
      const data = await workflowApi.getStepLog(project.id, step);
      setStepDetail(data);
    } catch {
      setStepDetail(null);
    } finally {
      setLoading(false);
    }
  };

  const handleRetry = () => {
    // Will be implemented with specific retry logic per step type
    setSelectedStep(null);
    setStepDetail(null);
  };

  return (
    <div className="bg-gray-900 border border-gray-800 rounded-xl p-4">
      <h3 className="text-sm font-semibold text-gray-300 mb-3 uppercase tracking-wider">
        {episodeLabel ? `${episodeLabel} · ` : ''}创作流程
      </h3>
      <div className="space-y-1">
        {STEP_ORDER.filter(key => steps[key]).map((step) => {
          const info = steps[step];
          return (
            <FlowStep
              key={step}
              step={step}
              status={info.status}
              summary={info.completedAt ? `完成于 ${new Date(info.completedAt).toLocaleTimeString()}` : ''}
              onClick={() => handleStepClick(step)}
            />
          );
        })}
      </div>

      {/* Progress bar */}
      {progress && (
        <div className="mt-4">
          <div className="flex justify-between text-xs text-gray-500 mb-1">
            <span>整体进度</span>
            <span>{progress.overallProgress || 0}%</span>
          </div>
          <div className="w-full h-1.5 bg-gray-800 rounded-full overflow-hidden">
            <div
              className="h-full bg-blue-500 rounded-full transition-all duration-500"
              style={{ width: `${progress.overallProgress || 0}%` }}
            />
          </div>
        </div>
      )}

      {/* Log Detail Modal */}
      {selectedStep && (
        <LogDetailModal
          log={loading ? { step: selectedStep, status: 'RUNNING' } : stepDetail}
          onClose={() => { setSelectedStep(null); setStepDetail(null); }}
          onRetry={stepDetail?.status === 'FAILED' ? handleRetry : null}
        />
      )}
    </div>
  );
}

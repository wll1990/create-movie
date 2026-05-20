import { Check, Loader2, X, Circle } from 'lucide-react';
import clsx from 'clsx';
import { STEP_LABELS, STEP_DESCRIPTIONS } from '../../constants/stepLabels';

export default function FlowStep({ step, status, summary, onClick }) {
  const isCompleted = status === 'COMPLETED';
  const isRunning = status === 'RUNNING';
  const isFailed = status === 'FAILED';

  return (
    <div
      onClick={onClick}
      title={STEP_DESCRIPTIONS[step] || ''}
      className={clsx(
        'flex items-center gap-3 px-4 py-3 rounded-lg cursor-pointer transition-all border',
        isCompleted && 'border-green-700 bg-green-900/20 hover:bg-green-900/30',
        isRunning && 'border-blue-700 bg-blue-900/20 hover:bg-blue-900/30',
        isFailed && 'border-red-700 bg-red-900/20 hover:bg-red-900/30',
        !isCompleted && !isRunning && !isFailed && 'border-gray-700 bg-gray-800/50 hover:bg-gray-800'
      )}
    >
      <div className="shrink-0">
        {isCompleted && <Check className="w-5 h-5 text-green-400" />}
        {isRunning && <Loader2 className="w-5 h-5 text-blue-400 animate-spin" />}
        {isFailed && <X className="w-5 h-5 text-red-400" />}
        {!isCompleted && !isRunning && !isFailed && (
          <Circle className="w-5 h-5 text-gray-600" />
        )}
      </div>

      <div className="flex-1 min-w-0">
        <div className={clsx(
          'text-sm font-medium',
          isCompleted && 'text-green-300',
          isRunning && 'text-blue-300',
          isFailed && 'text-red-300',
          !isCompleted && !isRunning && !isFailed && 'text-gray-400'
        )}>
          {STEP_LABELS[step] || step}
        </div>
        {summary ? (
          <div className="text-xs text-gray-500 truncate mt-0.5">{summary}</div>
        ) : (
          <div className="text-xs text-gray-600 truncate mt-0.5">
            {STEP_DESCRIPTIONS[step]}
          </div>
        )}
      </div>

      <div className="text-xs text-gray-600 shrink-0">详情 →</div>
    </div>
  );
}

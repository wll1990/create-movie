import { X, Clock, AlertCircle, Copy } from 'lucide-react';
import { STEP_LABELS } from '../../constants/stepLabels';

export default function LogDetailModal({ log, onClose, onRetry }) {
  if (!log) return null;

  const isFailed = log.status === 'FAILED';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60"
         onClick={onClose}>
      <div className="bg-gray-900 border border-gray-700 rounded-xl w-full max-w-2xl mx-4 max-h-[80vh] overflow-y-auto"
           onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-gray-700">
          <div>
            <h3 className="text-lg font-semibold">
              {STEP_LABELS[log.step] || log.step}
            </h3>
            <div className="flex items-center gap-2 mt-1">
              <span className={`text-sm px-2 py-0.5 rounded ${
                log.status === 'COMPLETED' ? 'bg-green-900/50 text-green-400' :
                log.status === 'RUNNING' ? 'bg-blue-900/50 text-blue-400' :
                log.status === 'FAILED' ? 'bg-red-900/50 text-red-400' :
                'bg-gray-700 text-gray-400'
              }`}>
                {log.status}
              </span>
              {log.llmResponseTimeMs > 0 && (
                <span className="flex items-center gap-1 text-xs text-gray-500">
                  <Clock className="w-3 h-3" />
                  {log.llmResponseTimeMs}ms
                </span>
              )}
            </div>
          </div>
          <button onClick={onClose}
                  className="p-1 hover:bg-gray-700 rounded-lg transition-colors">
            <X className="w-5 h-5 text-gray-400" />
          </button>
        </div>

        {/* Body */}
        <div className="p-4 space-y-4">
          {/* Error */}
          {isFailed && log.errorMessage && (
            <div className="flex items-start gap-2 p-3 bg-red-900/20 border border-red-800 rounded-lg">
              <AlertCircle className="w-4 h-4 text-red-400 mt-0.5 shrink-0" />
              <p className="text-sm text-red-300">{log.errorMessage}</p>
            </div>
          )}

          {/* Prompt */}
          {log.prompt && (
            <div>
              <div className="flex items-center justify-between mb-2">
                <h4 className="text-xs font-semibold text-blue-400 uppercase tracking-wider">
                  LLM 提示词 (Prompt)
                </h4>
                <button
                  onClick={() => navigator.clipboard.writeText(log.prompt)}
                  className="inline-flex items-center gap-1 px-2 py-1 text-xs bg-gray-700 hover:bg-gray-600 rounded transition-colors"
                >
                  <Copy className="w-3 h-3" />
                  复制
                </button>
              </div>
              <pre className="text-xs text-gray-300 bg-gray-800 rounded-lg p-3 whitespace-pre-wrap max-h-64 overflow-y-auto border border-gray-700">
                {log.prompt}
              </pre>
            </div>
          )}

          {/* Input */}
          {log.inputData && Object.keys(log.inputData).length > 0 && (
            <div>
              <h4 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
                输入数据
              </h4>
              <pre className="text-xs text-gray-300 bg-gray-800 rounded-lg p-3 whitespace-pre-wrap max-h-40 overflow-y-auto">
                {JSON.stringify(log.inputData, null, 2)}
              </pre>
            </div>
          )}

          {/* Output */}
          {log.outputData && Object.keys(log.outputData).length > 0 && (
            <div>
              <h4 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
                输出结果
              </h4>
              <pre className="text-xs text-gray-300 bg-gray-800 rounded-lg p-3 whitespace-pre-wrap max-h-60 overflow-y-auto">
                {JSON.stringify(log.outputData, null, 2)}
              </pre>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-3 p-4 border-t border-gray-700">
          <button onClick={onClose}
                  className="px-4 py-2 text-sm text-gray-400 hover:bg-gray-800 rounded-lg transition-colors">
            关闭
          </button>
          {isFailed && onRetry && (
            <button onClick={onRetry}
                    className="px-4 py-2 text-sm bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors">
              重新生成
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

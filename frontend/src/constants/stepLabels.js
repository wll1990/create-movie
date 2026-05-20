export const STEP_LABELS = {
  TOPIC_DESIGN: '选题构思',
  SCRIPT_CREATION: '剧本创作',
  CHARACTER_DESIGN: '人设设计',
  STORYBOARD_DESIGN: '分镜设计',
  VOICE_GENERATION: '配音生成',
  CLIP_GENERATION: '视频片段生成',
  FINAL_COMPOSITION: '最终合成',
  COPYWRITING: '文案发布',
};

export const STEP_DESCRIPTIONS = {
  TOPIC_DESIGN: '确定赛道和主题方向，为后续创作奠定基础',
  SCRIPT_CREATION: 'AI 自动生成包含场景、台词、情绪标记的完整剧本',
  CHARACTER_DESIGN: '为每个角色生成立绘、三视图和5种表情差分图',
  STORYBOARD_DESIGN: '将剧本拆解为24帧分镜，每帧标注景别、机位、角色位置',
  VOICE_GENERATION: '为每帧台词生成 TTS 配音音频',
  CLIP_GENERATION: '逐帧调用 AI 视频模型生成动画片段，每帧可审核重试',
  FINAL_COMPOSITION: 'FFmpeg 拼接所有片段 + 字幕 + BGM，输出成品 MP4',
  COPYWRITING: 'AI 生成发布标题、简介、话题标签和封面描述',
};

export const STEP_ORDER = [
  'TOPIC_DESIGN',
  'SCRIPT_CREATION',
  'CHARACTER_DESIGN',
  'STORYBOARD_DESIGN',
  'VOICE_GENERATION',
  'CLIP_GENERATION',
  'FINAL_COMPOSITION',
  'COPYWRITING',
];

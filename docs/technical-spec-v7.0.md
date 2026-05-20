# 真人漫剧爆款视频归纳+二创全流程 - 技术方案 v7.0 (完整版)

## 一、需求分析

### 1.1 核心目标

**双轨并行能力 + 可视化流程展示：**

1. **爆款分析**：从爆款视频提取可复用资产（内容/视觉/音频/流量四维基因）
2. **从零创作**：直接生成完整漫剧（选题→剧本→人设→分镜→合成→文案）
3. **混合模式**：分析驱动的创作（继承爆款基因，换赛道/主题重新生成）
4. **流程可视化**：前端展示完整6步创作流程状态，每步可点击查看详情

### 1.2 技术选型决策

| 层级 | 技术 | 版本 | 选型理由 |
|-----|------|------|---------|
| 前端 | React + Vite | 18+ | UI丰富，生态成熟 |
| 样式 | TailwindCSS | 3+ | 原子化CSS，快速开发 |
| 状态管理 | Redux Toolkit | 1+ | 可预测状态管理 |
| 路由 | React Router | 6+ | 标准前端路由 |
| 后端 | Java + Spring Boot | 21 + 3.2+ | 用户熟悉，企业级稳定性 |
| 数据库 | PostgreSQL + pgvector | 15+ | 成熟稳定，支持JSON和向量检索 |
| 对象存储 | MinIO | 2024+ | 开源S3兼容 |
| 视频处理 | FFmpeg | 6.0+ | 功能完整，跨平台 |
| AI服务 | 国产大模型API | - | 通过HTTP调用 |
| TTS | Edge TTS / 火山引擎 | - | 免费默认+付费备选 |
| 文生图 | 通义万相/Stable Diffusion | - | 通过HTTP调用 |

---

## 二、整体架构

```
┌─────────────────────────────────────────────────────────┐
│                     前端                              │
│   React 18 + TailwindCSS + Redux Toolkit             │
└─────────────────────────┬─────────────────────────────┘
                          │ REST API
┌─────────────────────────▼─────────────────────────────┐
│                     后端                              │
│   Spring Boot 3.2 + Java 21                          │
│   ┌────────────────────────────────────────────────┐  │
│   │  Controller层  │  Service层  │  Repository层  │  │
│   └────────────────────────────────────────────────┘  │
│   ┌────────────────────────────────────────────────┐  │
│   │  核心链路:                                      │  │
│   │  VideoAnalyzer → GeneToTemplateMapper          │  │
│   │       → ScriptCreator → StoryboardGenerator    │  │
│   │       → CharacterDesigner → VideoComposer      │  │
│   └────────────────────────────────────────────────┘  │
└─────────────────────────┬─────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        ▼                 ▼                  ▼
   PostgreSQL        MinIO (S3)        External AI APIs
   + pgvector        素材+成片           LLM/TTS/STT/ImageGen
```

---

## 三、核心数据模型

### 3.1 实体关系总览

```
project ──1:N── script ──1:N── scene
  │                  │
  │                  └──1:N── dialogue (JSONB in scene)
  │
  ├──1:N── character ── expressions (JSONB)
  │
  ├──1:N── storyboard ──1:N── storyboard_frame
  │                            │
  │                            └── references character.expression
  │
  ├──1:N── composition ──1:N── composition_task
  │
  ├──1:1── video_gene (when mode=ANALYSIS)
  │
  ├──1:1── creation_template
  │
  └──1:N── workflow_log

material ── (独立, project_id 可为 null=系统预置)
```

### 3.2 分析→创作的核心桥梁：CreationTemplate

```
爆款视频 → [VideoAnalyzerService] → VideoGene
                                        │
                          ┌─────────────┴─────────────┐
                          │  GeneToTemplateMapper      │
                          │  基因 → 创作模板映射        │
                          └─────────────┬─────────────┘
                                        │
                          CreationTemplate
                          ├── narrativeConfig
                          ├── visualStyleConfig
                          ├── audioProfileConfig
                          └── pacingRule
                                        │
                    ┌───────────────────┴───────────────────┐
                    │  ScriptCreatorService                 │
                    │  StoryboardGeneratorService           │
                    │  各模块读取模板对应部分构建prompt       │
                    └───────────────────────────────────────┘
```

CreationTemplate 支持**选择性继承**：用户可勾选"继承节奏+视觉风格，但换成甜宠赛道"，系统只替换赛道维度的配置。

### 3.3 VideoGene 结构（爆款分析输出）

```java
VideoGene {
    track: String                    // 赛道
    contentGene: {
        narrativePattern: String     // "三幕剧"/"反转递进"/"金句串烧"
        emotionalBeats: [{time:0s, type:"hook", intensity:0.9}, ...]
        tropeTags: ["先婚后爱", "身份反转"]
        dialogueDensity: Double     // 台词密度(字/秒)
    }
    visualGene: {
        aspectRatio: "9:16"
        avgShotDuration: 2.3        // 秒
        shotSequence: [{type:"CU", duration:1.5}, ...]
        colorPalette: "暖色调#FF6B6B"
        textOverlayRatio: 0.15
    }
    audioGene: {
        bgmStyle: "轻快电子"
        bgmBpm: 120
        sfxTriggers: [{time:3s, sfx:"反转音效"}, ...]
        speechRate: 4.2             // 字/秒
    }
    trafficGene: {
        hookType: "悬念提问"
        hookDuration: 3
        retentionSpikes: [8, 22, 38] // 秒
        ctaStyle: "引导关注+下集预告"
    }
    embeddingVector: float[1536]    // pgvector, 用于相似检索
}
```

---

## 四、数据库设计（完整 DDL）

```sql
-- 项目
CREATE TABLE project (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(200) NOT NULL,
    track VARCHAR(50),
    mode VARCHAR(20) NOT NULL CHECK (mode IN ('ANALYSIS','CREATION','HYBRID')),
    status VARCHAR(20) DEFAULT 'DRAFT',
    source_video_gene_id UUID,
    creation_template_id UUID,
    progress JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 剧本
CREATE TABLE script (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    title VARCHAR(200),
    track VARCHAR(50),
    duration INT,
    content JSONB NOT NULL,
    version INT DEFAULT 1,
    status VARCHAR(20) DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 场景
CREATE TABLE scene (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    script_id UUID NOT NULL REFERENCES script(id) ON DELETE CASCADE,
    scene_number INT NOT NULL,
    location VARCHAR(200),
    time_of_day VARCHAR(50),
    summary TEXT,
    dialogues JSONB NOT NULL DEFAULT '[]',
    duration_estimate INT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 角色/人设
CREATE TABLE character (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(50),
    gender VARCHAR(20),
    age_range VARCHAR(30),
    personality TEXT,
    appearance JSONB DEFAULT '{}',
    expressions JSONB DEFAULT '[]',
    voice_config JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 分镜
CREATE TABLE storyboard (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    script_id UUID NOT NULL REFERENCES script(id),
    total_frames INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 分镜帧
CREATE TABLE storyboard_frame (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    storyboard_id UUID NOT NULL REFERENCES storyboard(id) ON DELETE CASCADE,
    scene_id UUID NOT NULL REFERENCES scene(id),
    frame_number INT NOT NULL,
    shot_type VARCHAR(50),
    camera_angle VARCHAR(50),
    bg_description TEXT,
    bg_image_url VARCHAR(500),
    characters JSONB NOT NULL DEFAULT '[]',
    dialogue_id UUID,
    subtitle_text TEXT,
    duration_sec DOUBLE PRECISION DEFAULT 3.0,
    transition VARCHAR(50) DEFAULT 'cut',
    status VARCHAR(20) DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 素材
CREATE TABLE material (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID,
    type VARCHAR(50) NOT NULL CHECK (type IN ('IMAGE','AUDIO','VIDEO','FONT','TEMPLATE')),
    category VARCHAR(50),
    name VARCHAR(200) NOT NULL,
    url VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500),
    metadata JSONB DEFAULT '{}',
    tags TEXT[],
    source VARCHAR(50) DEFAULT 'UPLOADED' CHECK (source IN ('UPLOADED','SYSTEM','AI_GENERATED')),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 成片
CREATE TABLE composition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    storyboard_id UUID NOT NULL REFERENCES storyboard(id),
    video_url VARCHAR(500),
    cover_url VARCHAR(500),
    duration_sec INT,
    resolution VARCHAR(20) DEFAULT '1080x1920',
    status VARCHAR(20) DEFAULT 'PENDING',
    progress INT DEFAULT 0,
    composition_config JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 合成任务队列
CREATE TABLE composition_task (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    composition_id UUID NOT NULL REFERENCES composition(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','PROCESSING','COMPLETED','FAILED')),
    progress INT DEFAULT 0,
    ffmpeg_command TEXT,
    ffmpeg_log TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

-- 视频基因
CREATE TABLE video_gene (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES project(id),
    track VARCHAR(50) NOT NULL,
    content_gene JSONB NOT NULL,
    visual_gene JSONB NOT NULL,
    audio_gene JSONB NOT NULL,
    traffic_gene JSONB NOT NULL,
    embedding_vector vector(1536),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 创作模板
CREATE TABLE creation_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_gene_id UUID REFERENCES video_gene(id),
    name VARCHAR(200) NOT NULL,
    narrative_config JSONB NOT NULL,
    visual_config JSONB NOT NULL,
    audio_config JSONB NOT NULL,
    pacing_config JSONB NOT NULL,
    editable BOOLEAN DEFAULT TRUE,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 流程日志
CREATE TABLE workflow_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    step VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED')),
    prompt TEXT,
    input_data JSONB DEFAULT '{}',
    output_data JSONB DEFAULT '{}',
    error_message TEXT,
    llm_response_time_ms INT,
    retry_count INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

---

## 五、工作流步骤定义

### 5.1 枚举（前后端共享，通过 OpenAPI 生成类型）

```java
public enum WorkflowStep {
    TOPIC_DESIGN("选题构思"),
    SCRIPT_CREATION("剧本创作"),
    CHARACTER_DESIGN("人设设计"),
    STORYBOARD_DESIGN("分镜设计"),
    VIDEO_COMPOSITION("视频合成"),
    COPYWRITING("文案生成");

    private final String displayName;
}
```

### 5.2 步骤与 API 对应

| 步骤 | 触发 API | 负责 Service |
|------|---------|-------------|
| TOPIC_DESIGN | POST /api/projects | ProjectService |
| SCRIPT_CREATION | POST /api/projects/{id}/scripts | ScriptService |
| CHARACTER_DESIGN | POST /api/projects/{id}/characters | CharacterService |
| STORYBOARD_DESIGN | POST /api/projects/{id}/storyboards | StoryboardService |
| VIDEO_COMPOSITION | POST /api/projects/{id}/compositions | VideoComposerService |
| COPYWRITING | POST /api/projects/{id}/copywriting | CopywritingService |

### 5.3 Project.progress 结构（物化缓存，每次 WorkflowLog 更新时计算）

```json
{
  "currentStep": "STORYBOARD_DESIGN",
  "totalSteps": 6,
  "completedSteps": 2,
  "steps": {
    "TOPIC_DESIGN":        {"status": "COMPLETED", "completedAt": "..."},
    "SCRIPT_CREATION":     {"status": "COMPLETED", "completedAt": "..."},
    "CHARACTER_DESIGN":    {"status": "RUNNING", "startedAt": "..."},
    "STORYBOARD_DESIGN":   {"status": "PENDING"},
    "VIDEO_COMPOSITION":   {"status": "PENDING"},
    "COPYWRITING":         {"status": "PENDING"}
  },
  "overallProgress": 33
}
```

---

## 六、API 接口设计

### 6.1 接口列表

| API路径 | 方法 | 功能 |
|--------|------|------|
| `/api/projects` | POST | 创建项目 |
| `/api/projects` | GET | 项目列表 |
| `/api/projects/{id}` | GET | 获取项目详情（含 progress） |
| `/api/projects/{id}` | DELETE | 删除项目 |
| `/api/projects/{id}/scripts` | POST | 生成剧本 |
| `/api/projects/{id}/scripts` | GET | 获取剧本 |
| `/api/projects/{id}/scripts` | PUT | 更新剧本 |
| `/api/projects/{id}/characters` | POST | 生成人设 |
| `/api/projects/{id}/characters` | GET | 获取人设列表 |
| `/api/projects/{id}/storyboards` | POST | 生成分镜 |
| `/api/projects/{id}/storyboards` | GET | 获取分镜 |
| `/api/projects/{id}/compositions` | POST | 提交视频合成 |
| `/api/projects/{id}/compositions` | GET | 获取合成列表 |
| `/api/projects/{id}/compositions/{compId}/progress` | GET | 获取合成进度 |
| `/api/projects/{id}/copywriting` | POST | 生成发布文案 |
| `/api/materials` | GET | 素材列表 |
| `/api/materials` | POST | 上传素材 |
| `/api/materials/{id}` | DELETE | 删除素材 |
| `/api/workflow/logs/{projectId}` | GET | 获取流程日志列表 |
| `/api/workflow/logs/{projectId}/{step}` | GET | 获取步骤详情 |

### 6.2 生成剧本接口（核心示例）

**POST /api/projects/{id}/scripts**

请求体：
```json
{
  "track": "都市甜宠",
  "theme": "霸道总裁爱上我",
  "duration": 45,
  "templateId": "optional-uuid",
  "templateInheritance": {
    "inheritNarrative": true,
    "inheritVisual": false,
    "inheritAudio": true,
    "inheritPacing": true
  },
  "characters": [
    {"name": "女主", "role": "PROTAGONIST", "personality": "温柔善良"},
    {"name": "男主", "role": "LOVE_INTEREST", "personality": "冷酷霸总"}
  ]
}
```

响应体：
```json
{
  "id": "uuid",
  "projectId": "uuid",
  "title": "霸道总裁的秘密",
  "track": "都市甜宠",
  "duration": 45,
  "scenes": [
    {
      "id": "uuid",
      "sceneNumber": 1,
      "location": "咖啡厅",
      "timeOfDay": "下午",
      "summary": "女主在咖啡厅打工，男主意外闯入",
      "dialogues": [
        {
          "characterId": "uuid-女主",
          "characterName": "女主",
          "text": "先生，您的咖啡...",
          "emotion": "surprised",
          "durationEstimate": 2.5
        }
      ],
      "durationEstimate": 15
    }
  ],
  "createdAt": "2024-01-01T12:00:00"
}
```

---

## 七、LLM 调用可靠性方案

### 7.1 客户端选型

使用 Spring 6.1 的 `RestClient`（同步），**不使用** `WebClient.block()`。

```java
@Component
public class LlmClient {
    private final RestClient restClient;
    private final String model;

    public LlmClient(@Value("${llm.api-key}") String apiKey,
                     @Value("${llm.api-base}") String apiBase,
                     @Value("${llm.model}") String model,
                     RestClient.Builder builder) {
        this.model = model;
        this.restClient = builder
            .baseUrl(apiBase)
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .build();
    }

    public String generate(String prompt) {
        return restClient.post()
            .uri("/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new LlmRequest(model, prompt, 1024))
            .retrieve()
            .body(LlmResponse.class)
            .getContent();
    }
}
```

### 7.2 三层防御

| 层 | 机制 | 说明 |
|----|------|------|
| 源头约束 | `response_format: json_object` 或 function calling | 让 LLM 输出合法 JSON |
| 解析校验 | Jackson + JSON Schema validator | 校验结构完整性，最多重试3次 |
| 熔断降级 | Resilience4j CircuitBreaker | 5次失败→熔断60秒→半开探测2次 |

### 7.3 熔断与重试配置

```yaml
resilience4j:
  circuitbreaker:
    instances:
      llm:
        failure-rate-threshold: 50
        sliding-window-size: 10
        minimum-number-of-calls: 5
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 2
  retry:
    instances:
      llm:
        max-attempts: 3
        wait-duration: 1s
        exponential-backoff-multiplier: 2

llm:
  api-key: ${LLM_API_KEY}
  api-base: ${LLM_API_BASE:https://api.example.com/v1}
  model: ${LLM_MODEL:gpt-4o}
  timeout:
    connect: 10s
    read: 120s
```

---

## 八、视频合成完整管线

### 8.1 管线步骤

```
Script + Storyboard + Character(立绘URL+表情) + Material(BGM/SFX)
        │
        ▼
1. TtsService.generateAll(dialogues)     → N个音频文件(.mp3)
2. TimelineAssemblerService.assemble()   → List<TimelineSegment>
   ├── 每句台词 = 1个 segment
   ├── segment.duration = TTS音频时长 + 头尾留白
   ├── segment.bgImage = 分镜帧背景图URL
   ├── segment.characters = [{imageUrl, position:{x,y}, scale, layer}]
   ├── segment.subtitle = 台词文本
   └── segment.transition = 转场类型
3. FfmpegCommandBuilder.build(timeline)  → FFmpeg filter_complex 脚本
4. ProcessBuilder("ffmpeg", filter)       → 输出视频文件
5. MinioClient.upload(output)             → 上传MinIO
6. 更新Composition状态为COMPLETED
```

### 8.2 合成任务队列（异步，不阻塞API）

```java
@Service
public class CompositionScheduler {
    @Scheduled(fixedDelay = 5000)
    public void processQueue() {
        List<CompositionTask> tasks = taskRepository
            .findByStatusOrderByCreatedAt("QUEUED", Pageable.ofSize(2));
        for (CompositionTask task : tasks) {
            videoComposerService.executeAsync(task);
        }
    }
}
```

- composition_task 表做队列，Scheduler 每5秒拉取 QUEUED 任务
- 最大并行数 = 2（可配置）
- 前端轮询 `GET /api/projects/{id}/compositions/{compId}/progress`

### 8.3 FFmpeg 调用

```java
public void executeFfmpeg(String filterScript, String outputPath) throws IOException {
    List<String> command = List.of(
        "ffmpeg",
        "-filter_complex", filterScript,
        "-c:v", "libx264", "-crf", "23", "-preset", "fast",
        "-c:a", "aac", "-b:a", "128k",
        outputPath
    );

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.redirectErrorStream(true);
    Process process = pb.start();

    try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
            log.info("FFmpeg: {}", line);
        }
    }

    int exitCode = process.waitFor();
    if (exitCode != 0) {
        throw new RuntimeException("FFmpeg处理失败, exitCode=" + exitCode);
    }
}
```

### 8.4 关键新增 Service

```
service/
├── TimelineAssemblerService.java
├── TtsService.java              (Edge TTS 免费/火山引擎付费)
├── SubtitleService.java         (生成ASS字幕)
├── TransitionService.java
└── FfmpegCommandBuilder.java    (核心:生成filter_complex脚本)
```

---

## 九、素材体系

### 9.1 三个来源

| 来源 | 素材类型 | 实现方式 |
|------|---------|---------|
| 用户上传 | 参考视频、自备图片/音频 | MaterialController.upload() → MinIO |
| 系统预置 | 常用BGM、音效、字体、字幕样式 | 初始化脚本批量导入 material 表 |
| AI生成 | 人物立绘、场景背景、TTS语音 | 新增 client 类调用 AI API |

### 9.2 AI 客户端

```
client/
├── LlmClient.java              // LLM 文本生成 (RestClient)
├── ImageGenClient.java         // 文生图 (通义万相/Stable Diffusion)
├── TtsClient.java              // TTS (Edge TTS免费默认/火山引擎付费)
├── SpeechToTextClient.java     // 语音转文字 (Whisper API)
└── InfringementClient.java     // 侵权检测
```

### 9.3 对象存储（MinIO）的业务场景

| 业务场景 | 存储内容 |
|---------|---------|
| 视频素材库 | BGM音乐、音效文件 |
| 图片素材库 | 人物立绘、场景背景图 |
| 爆款分析源文件 | 用户上传的爆款视频 |
| 成片输出 | 合成后的最终视频 |
| 分镜草稿 | 分镜设计过程中的预览图 |
| 封面图片 | 视频封面图 |

---

## 十、前端技术方案

### 10.1 技术栈

| 分类 | 技术 | 版本 |
|-----|------|------|
| 框架 | React + Vite | 18+ |
| 样式 | TailwindCSS | 3+ |
| 状态管理 | Redux Toolkit | 1+ |
| 路由 | React Router | 6+ |
| 图标 | Lucide React | latest |
| 视频播放 | react-player | latest |
| API调用 | axios | 1+ |

### 10.2 前端目录结构

```
frontend/
├── src/
│   ├── components/
│   │   ├── Layout/
│   │   ├── FlowPanel/          # 流程进度面板
│   │   │   ├── FlowStep.jsx     # 单步骤节点（可点击）
│   │   │   └── LogDetailModal.jsx
│   │   ├── ScriptEditor/       # 剧本编辑器
│   │   ├── StoryboardEditor/   # 分镜编辑器
│   │   ├── MaterialLibrary/    # 素材库
│   │   └── VideoPreview/       # 视频预览
│   ├── pages/
│   │   ├── HomePage.jsx
│   │   ├── CreationPage.jsx
│   │   └── ProjectPage.jsx
│   ├── store/
│   ├── services/
│   │   └── workflowApi.js      # 流程日志 API 调用
│   ├── types/                  # OpenAPI 生成的 TypeScript 类型
│   └── utils/
├── package.json
└── vite.config.js
```

### 10.3 流程面板交互

```
┌──────────────────────────────────────────────────────────┐
│                    流程进度面板（可点击）                   │
├──────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────┐                │
│  │ ✅ 选题构思    [点击查看详情]        │                │
│  │   └─ 都市甜宠 · 45秒                │                │
│  ├─────────────────────────────────────┤                │
│  │ ✅ 剧本创作    [点击查看详情]        │                │
│  │   └─ LLM耗时: 4.2s                  │                │
│  ├─────────────────────────────────────┤                │
│  │ ⏳ 人设设计    [点击查看详情]        │                │
│  ├─────────────────────────────────────┤                │
│  │ ○ 分镜设计                          │                │
│  ├─────────────────────────────────────┤                │
│  │ ○ 视频合成                          │                │
│  ├─────────────────────────────────────┤                │
│  │ ○ 文案生成                          │                │
│  └─────────────────────────────────────┘                │
└──────────────────────────────────────────────────────────┘
```

---

## 十一、后端项目结构

```
backend/
├── src/main/java/com/example/makemovie/
│   ├── MakeMovieApplication.java
│   ├── controller/
│   │   ├── ProjectController.java
│   │   ├── ScriptController.java
│   │   ├── CharacterController.java
│   │   ├── StoryboardController.java
│   │   ├── MaterialController.java
│   │   ├── CompositionController.java
│   │   └── WorkflowLogController.java
│   ├── service/
│   │   ├── ProjectService.java
│   │   ├── ScriptService.java
│   │   ├── CharacterService.java
│   │   ├── StoryboardService.java
│   │   ├── MaterialService.java
│   │   ├── VideoAnalyzerService.java
│   │   ├── VideoComposerService.java
│   │   ├── CopywritingService.java
│   │   ├── WorkflowLogService.java
│   │   ├── ProgressService.java
│   │   ├── TimelineAssemblerService.java
│   │   ├── TtsService.java
│   │   ├── SubtitleService.java
│   │   ├── TransitionService.java
│   │   ├── FfmpegCommandBuilder.java
│   │   └── GeneToTemplateMapper.java
│   ├── client/
│   │   ├── LlmClient.java
│   │   ├── ImageGenClient.java
│   │   ├── TtsClient.java
│   │   ├── SpeechToTextClient.java
│   │   └── InfringementClient.java
│   ├── entity/
│   │   ├── Project.java
│   │   ├── Script.java
│   │   ├── Scene.java
│   │   ├── Character.java
│   │   ├── Storyboard.java
│   │   ├── StoryboardFrame.java
│   │   ├── Material.java
│   │   ├── Composition.java
│   │   ├── CompositionTask.java
│   │   ├── VideoGene.java
│   │   ├── CreationTemplate.java
│   │   └── WorkflowLog.java
│   ├── enums/
│   │   ├── WorkflowStep.java
│   │   ├── StepStatus.java
│   │   ├── ShotType.java
│   │   ├── ProjectMode.java
│   │   └── MaterialType.java
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   ├── config/
│   │   ├── WebConfig.java
│   │   ├── MinioConfig.java
│   │   ├── AsyncConfig.java
│   │   └── RetryConfig.java
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   └── BusinessException.java
│   ├── validation/
│   │   └── JsonSchemaValidator.java
│   └── repository/
│       ├── ProjectRepository.java
│       ├── ScriptRepository.java
│       ├── SceneRepository.java
│       ├── CharacterRepository.java
│       ├── StoryboardRepository.java
│       ├── StoryboardFrameRepository.java
│       ├── MaterialRepository.java
│       ├── CompositionRepository.java
│       ├── CompositionTaskRepository.java
│       ├── VideoGeneRepository.java
│       ├── CreationTemplateRepository.java
│       └── WorkflowLogRepository.java
├── src/test/java/
│   ├── unit/
│   ├── integration/
│   └── fixtures/
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-prod.yml
│   ├── schemas/
│   │   ├── script-output-schema.json
│   │   ├── storyboard-output-schema.json
│   │   └── video-gene-output-schema.json
│   └── db/migration/
├── pom.xml
└── Dockerfile
```

---

## 十二、核心配置

### 12.1 application.yml

```yaml
server:
  port: 8080

spring:
  application:
    name: make-movie
  datasource:
    url: jdbc:postgresql://localhost:5432/make_movie
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration

minio:
  endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
  access-key: ${MINIO_ACCESS_KEY:minioadmin}
  secret-key: ${MINIO_SECRET_KEY:minioadmin}
  bucket-name: ${MINIO_BUCKET:assets}

llm:
  api-key: ${LLM_API_KEY:}
  api-base: ${LLM_API_BASE:https://api.example.com/v1}
  model: ${LLM_MODEL:gpt-4o}
  timeout:
    connect: 10s
    read: 120s

storage:
  upload-dir: ./uploads
  temp-dir: ./temp

video:
  composition:
    max-parallel-tasks: 2
    poll-interval: 5s
```

### 12.2 生产环境凭据管理

生产环境通过环境变量注入，**绝不**硬编码凭据：

```yaml
# application-prod.yml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}

minio:
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}

llm:
  api-key: ${LLM_API_KEY}
```

---

## 十三、关键依赖（pom.xml）

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version>
</parent>

<properties>
    <java.version>21</java.version>
</properties>

<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- PostgreSQL -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Flyway -->
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>

    <!-- MinIO -->
    <dependency>
        <groupId>io.minio</groupId>
        <artifactId>minio</artifactId>
        <version>8.5.7</version>
    </dependency>

    <!-- Resilience4j -->
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-spring-boot3</artifactId>
        <version>2.2.0</version>
    </dependency>

    <!-- JSON Schema Validation -->
    <dependency>
        <groupId>com.networknt</groupId>
        <artifactId>json-schema-validator</artifactId>
        <version>1.4.0</version>
    </dependency>

    <!-- SpringDoc OpenAPI -->
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>2.5.0</version>
    </dependency>

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- Test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>minio</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## 十四、部署方案（Docker Compose）

```yaml
services:
  frontend:
    build: ./frontend
    ports:
      - "3000:3000"
    depends_on:
      - api

  api:
    build: ./backend
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      minio:
        condition: service_started
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/make_movie
      - SPRING_DATASOURCE_USERNAME=admin
      - SPRING_DATASOURCE_PASSWORD=password
      - MINIO_ENDPOINT=http://minio:9000
      - LLM_API_KEY=${LLM_API_KEY}
      - LLM_API_BASE=${LLM_API_BASE}

  postgres:
    image: pgvector/pgvector:pg16
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U admin -d make_movie"]
      interval: 5s
      timeout: 5s
      retries: 5
    volumes:
      - postgres_data:/var/lib/postgresql/data
    environment:
      POSTGRES_DB: make_movie
      POSTGRES_USER: admin
      POSTGRES_PASSWORD: password

  minio:
    image: minio/minio:latest
    volumes:
      - minio_data:/data
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    ports:
      - "9000:9000"
      - "9001:9001"

volumes:
  postgres_data:
  minio_data:
```

---

## 十五、测试策略

| 层级 | 工具 | 范围 |
|------|------|------|
| 单元测试 | JUnit 5 + Mockito | Service 逻辑 + Mock LLM client（fixture JSON） |
| | | FfmpegCommandBuilder 输出验证 |
| | | JSON Schema 校验（合法+非法输入） |
| 集成测试 | @SpringBootTest + Testcontainers | PostgreSQL + MinIO 全链路 |
| | | FFmpeg 真实合成（2帧测试图片） |
| LLM 质量 | Golden fixtures | prompt 变更后验证输出结构（手动触发，不入CI） |
| E2E | Playwright | 完整创作流程 UI 测试 |

---

## 十六、实施顺序

| 阶段 | 内容 | 可验证产出 |
|------|------|-----------|
| **Phase 1: 骨架** | Spring Boot 初始化、实体、Flyway DDL、Project CRUD | POST /api/projects → 数据库有记录 |
| **Phase 2: 剧本** | ScriptService + LlmClient + JSON校验 + 重试 + WorkflowLog | 输入主题 → 返回合法剧本 JSON |
| **Phase 3: 人设+分镜** | CharacterService + StoryboardService + ImageGenClient | 生成人设立绘和分镜帧 |
| **Phase 4: 合成** | TtsService + TimelineAssembler + FfmpegCommandBuilder + 队列 | 2张图+1段TTS → 可播放视频 |
| **Phase 5: 分析** | VideoAnalyzerService + STT + VideoGene + CreationTemplate | 上传视频 → 四维基因JSON → 生成模板 |
| **Phase 6: 前端** | React + 流程面板 + 剧本编辑 + 分镜编辑 + 视频预览 | 完整走通创作流程 UI |
| **Phase 7: 文案+发布** | CopywritingService + 测试完善 + Docker Compose | 一键部署运行 |

---

## 十七、验证方式

1. Phase 1: `POST /api/projects` → 数据库有记录
2. Phase 2: `POST /api/projects/{id}/scripts` → 返回合法剧本 JSON，WorkflowLog 有记录
3. Phase 4: 2张测试图片+1段TTS音频 → 合成预览视频可播放
4. Phase 5: 上传45秒爆款视频 → VideoGene JSON 四个维度完整 → 创建模板 → 生成新剧本
5. Phase 6: 前端走通"新建→生成剧本→查看分镜→预览视频"
6. Phase 7: `docker compose up` → 所有服务正常启动

---

**版本**: v7.0 (完整版)
**后端语言**: Java 21 + Spring Boot 3.2
**核心能力**: 爆款分析 + 从零创作 + 混合模式 + 可视化流程

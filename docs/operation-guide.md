# MakeMovie 操作手册

---

## 技术栈

### 后端

| 组件 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 21 |
| 框架 | Spring Boot | 3.2.5 |
| ORM | Spring Data JPA + Hibernate | - |
| 数据库 | PostgreSQL + pgvector | 16 |
| 迁移 | Flyway | - |
| 对象存储 | MinIO（本地） + 阿里云 OSS（公网） | - |
| 日志 | Logback | - |

### 前端

| 组件 | 技术 |
|------|------|
| 框架 | React 18 |
| 构建 | Vite 5 |
| 样式 | Tailwind CSS |
| 路由 | React Router 6 |
| 状态 | Redux Toolkit |
| HTTP | Axios |
| 图标 | Lucide React |

### 外部服务

| 服务 | 用途 | API 地址 | 配置变量 |
|------|------|----------|----------|
| DeepSeek | 主 LLM（剧本/人设/分镜/文案） | `api.deepseek.com` | `LLM_API_*` |
| 通义千问 VL | 多模态视觉分析 | `dashscope.aliyuncs.com` | `LLM_VISION_*` |
| 通义万相 wanx-v1 | 文生图（角色立绘/背景图） | `dashscope.aliyuncs.com` | `IMAGE_GEN_*` |
| 通义万相 wan2.7-i2v | 图生视频（视频片段） | `dashscope.aliyuncs.com` | `VIDEO_GEN_*` |
| Edge-TTS | 语音合成（免费，本地子进程） | 本地 CLI | `TTS_*` |
| 阿里云 OSS | 图片公网存储（Wan2.7 需要公网 URL） | `oss-cn-beijing.aliyuncs.com` | `OSS_*` |
| FFmpeg | 视频合成/字幕叠加/BGM 混音 | 本地 CLI | - |

### 本地基础设施

| 服务 | 用途 | 端口 |
|------|------|------|
| PostgreSQL | 业务数据库 | 5432 |
| MinIO | 本地对象存储 | 9000（API）/ 9001（控制台）|
| 后端 API | Spring Boot | 8080 |
| 前端 | Vite Dev Server | 3000 |

---

## 零、系统启动

### 前置条件

```bash
# 1. 确认 Java 21 已安装
java -version   # 应显示 21.x

# 2. 确认 Docker 已安装（用于 PostgreSQL + MinIO）
docker -v

# 3. 配置 API Key 和 OSS
cp .env.example .env
# 编辑 .env，填入必填项（见下方配置说明）
```

### 必填配置

| 配置项 | .env 变量 | 说明 |
|--------|----------|------|
| 主 LLM | `LLM_API_KEY` | DeepSeek API Key（剧本生成等核心功能） |
| 文生图 | `IMAGE_GEN_API_KEY` | 通义万相 DashScope Key（角色立绘和背景） |
| 图生视频 | `VIDEO_GEN_API_KEY` | 通义万相 DashScope Key（与文生图共用） |
| 阿里云 OSS | `OSS_*` | 公网图片存储（Wan2.7 图生视频需要公网 URL） |

### 启动

```bash
# Docker 方式（推荐）
docker compose up -d

# 或本地开发方式
# 先确保 PostgreSQL 和 MinIO 已启动，然后：
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd frontend && npm run dev
```

### 停止

```bash
docker compose down
```

---

## 一、创建项目

### 1.1 打开新建页面

浏览器访问 `http://localhost:3000` → 点击左侧 **"新建项目"**。

### 1.2 选择模式

| 模式 | 适用场景 | 你需要提供 |
|------|---------|-----------|
| **创作模式** | 从零生成一个新漫剧 | 项目名称 + 赛道 + 主题 |
| **分析模式** | 研究一个爆款视频 | 项目名称 + 上传视频 |
| **混合模式** | 参考爆款视频的风格生成新内容 | 项目名称 + 赛道 + 主题 + 上传视频 |

### 1.3 选择赛道

| 赛道 | 适合题材 |
|------|---------|
| 都市甜宠 | 霸道总裁、办公室恋爱、先婚后爱 |
| 悬疑反转 | 悬疑推理、惊悚、身份反转 |
| 古装仙侠 | 古风修仙、宫斗、玄幻 |
| 现代言情 | 校园恋爱、都市情感、青春 |
| 科幻奇幻 | 末世科幻、超能力、异世界 |
| 青春校园 | 校园日常、学生会、社团 |

### 1.4 填写主题

一句话描述你想讲的故事，例如：
- "霸道总裁爱上咖啡店打工女孩"
- "女律师穿越到修仙世界成为废柴弟子"
- "AI机器人觉醒后伪装成大学生"

点击 **"创建项目"** → 自动跳转到项目工作台。

---

## 二、工作台操作

### 2.1 界面布局

```
┌────────────────┬─────────────────────────────────┐
│  左侧边栏       │  右侧内容区                       │
│                │                                 │
│  📋 流程面板    │  📝 剧本编辑器                    │
│  (8步状态)     │                                 │
│                │  👤 角色卡片                      │
│  📹 视频分析    │                                 │
│  (分析模式)     │  🎬 分镜预览                      │
│                │                                 │
│  ⚡ 快捷操作    │  🎥 视频预览                      │
│  1.生成剧本    │                                 │
│  2.生成人设    │                                 │
│  3.生成分镜    │                                 │
│  4.生成配音    │                                 │
│  5.初始化片段  │                                 │
│  6.最终合成    │                                 │
└────────────────┴─────────────────────────────────┘
```

### 2.2 流程面板说明

左侧流程面板显示 8 个步骤的实时状态：

| 图标 | 状态 | 含义 |
|------|------|------|
| ✅ 绿色 | COMPLETED | 该步骤已完成 |
| 🔄 蓝色 | RUNNING | 该步骤正在执行 |
| ❌ 红色 | FAILED | 该步骤失败，可点击查看原因 |
| ○ 灰色 | PENDING | 该步骤尚未开始 |

**点击任意步骤** → 弹出详情弹窗，包含：LLM prompt、输入数据、输出结果、响应耗时。

---

## 三、逐步骤操作

### 步骤 1：选题构思（自动完成）

创建项目后自动标记完成。分析模式下会上传视频并提取 VideoGene。

---

### 步骤 2：剧本创作

点击快捷操作区的 **"1. 生成剧本"**。

系统自动：
1. 根据赛道和主题调用 LLM 生成剧本
2. 剧本包含多个场景，每个场景有台词、角色、情绪标记
3. 最多自动重试 3 次（JSON 格式校验）

**产出文件**：`01-script/script_v1.json`、`01-script/script_v1.txt`

**你可干预**：
- 在右侧剧本编辑器中修改任意台词
- 调整角色情绪标记（neutral/happy/sad/surprised/angry）
- 修改后点"保存"，剧本版本号自动 +1

---

### 步骤 3：人设设计

点击快捷操作区的 **"2. 生成人设"**（需步骤2完成）。

系统自动：
1. 从剧本提取所有角色名称
2. 为每个角色调用 LLM 生成外貌/性格设定
3. 调用通义万相生成：
   - **立绘**（正面脸部特写，1024×1024）
   - **三视图**（前/侧/背三个角度，1280×720）⭐ 这是后续视频生成的角色锚点
   - **5 种表情差分**：neutral/happy/sad/surprised/angry（以三视图为参考图）

**产出文件**：
```
02-characters/{角色名}/
    ├── design.json      # 角色设定数据
    ├── portrait.png     # 脸部立绘
    ├── threeview.png    # ⭐ 三视图锚点
    └── expressions/
        ├── neutral.png / happy.png / sad.png / surprised.png / angry.png
```

**你可干预**：
- 查看角色卡片，确认外貌描述是否符合预期
- 表情图不理想时，可重新生成单个角色

---

### 步骤 4：分镜设计

点击快捷操作区的 **"3. 生成分镜"**（需步骤2完成）。

系统自动：
1. LLM 分析剧本，生成分镜表（24 帧）
2. 每帧包含：景别/机位/背景描述/角色位置/表情/台词/时长/转场
3. 为每帧生成**视频生成专用 prompt**（用于步骤6的 AI 视频生成）
4. 异步生成场景背景图

**产出文件**：
```
03-storyboard/
    ├── storyboard.json
    ├── backgrounds/
    │   ├── scene_01_{hash}.png   # 按场景去重的背景图
    │   └── scene_02_{hash}.png
    ├── frames/{NNN}/
    │   └── frame_data.json       # 帧元数据
    └── prompts/
        ├── frame_001.txt         # 视频生成 prompt
        └── frame_024.txt
```

**你可干预**：
- 在右侧分镜预览中查看每帧构图
- 修改某个帧的 prompt（点击帧 → 编辑 prompt）
- prompt 越详细，最终视频质量越高

---

### 步骤 5：配音生成

点击快捷操作区的 **"4. 生成配音"**（需步骤4完成）。

系统自动：
1. 为每个角色读取 voiceConfig（默认 `zh-CN-XiaoxiaoNeural`）
2. 调用 edge-tts 为每帧台词生成 MP3 音频
3. 保存语音配置到 `04-voice/voice_config.json`

**产出文件**：
```
04-voice/
    ├── voice_config.json     # 各角色语音参数
    └── audio/
        ├── frame_001.mp3 ... frame_024.mp3
```

**你可干预**：
- 后续可扩展：为每个角色配置不同的 TTS 语音

---

### 步骤 6：视频片段生成（逐帧操作）⭐ 核心

点击快捷操作区的 **"5. 初始化视频片段"**。

这是最关键的一步。系统会**逐帧循环**，让你审核每一帧的视频生成：

#### 逐帧操作流程

```
帧 01: 📋 查看 prompt → 满意 → [确认生成] → 🎬 AI生成视频 → 预览 → [通过 ✓]
帧 02: 📋 查看 prompt → 修改后 → [确认生成] → 🎬 AI生成视频 → 不满意 → [重试 🔄]
帧 02(重试): 📋 编辑prompt → [确认生成] → 🎬 生成 → 满意 → [通过 ✓]
帧 03: 📋 查看 prompt → [确认生成] → 🎬 生成失败 → [重试 🔄]
帧 03(重试): 📋 不改prompt → [确认生成] → 🎬 生成 → [通过 ✓]
...
帧 24: [通过 ✓] → 🎉 全部完成，自动触发步骤7
```

#### 每个操作按钮

| 按钮 | 作用 |
|------|------|
| **确认生成** | 将当前帧的 prompt + 角色参考图 + 背景图 发送给视频生成 API |
| **通过** | 审核通过，推进到下一帧 |
| **重试** | 回到 prompt 编辑状态，可修改后重新生成 |
| **跳过** | 跳过当前帧（该帧将使用静态背景+配音，FFmpeg 处理） |

#### 传给视频生成 API 的数据

```
帧03 的实际请求:
  prompt:
    "参考图中的女性角色【苏晴】，24岁，黑色长发，白色连衣裙，
     当前表情：惊讶，口型同步台词'什么？你是...'，
     背景：咖啡厅室内，暖色调灯光，下午时分，
     镜头：中近景(MCU)，平视机位，
     动作：轻微身体后仰(惊讶反应)，自然呼吸起伏，
     风格：红果剧场漫剧风格，日系动画质感，柔和光影，9:16竖屏。"
  
  referenceImage:    02-characters/苏晴/threeview.png   ← 保证长相一致
  expressionImage:   02-characters/苏晴/expressions/surprised.png
  backgroundImage:   03-storyboard/backgrounds/scene_01_cafe.png
```

**产出文件**：
```
05-video-clips/clips/
    ├── frame_001.mp4 ... frame_024.mp4
```

**所有帧都生成通过了，才会进入步骤7。**

---

### 步骤 7：最终合成

步骤6 全部完成后自动触发，或点击 **"6. 最终合成"**。

系统自动：
1. 下载每帧的视频片段和配音音频
2. FFmpeg concat 拼接所有视频片段
3. 生成 ASS 字幕文件并叠加到视频上
4. 混合所有音频轨道
5. 输出 1080×1920 竖屏 MP4

**产出文件**：
```
06-final/
    ├── output.mp4           # 最终成品
    ├── subtitles.ass        # 字幕文件
    └── cover.png            # 封面图(待实现)
```

---

### 步骤 8：文案发布

步骤7 完成后自动触发。

系统自动调用 LLM 生成：
- 视频标题（20 字以内）
- 视频简介（50-100 字）
- 5-8 个话题标签
- 封面图描述

**产出文件**：`06-final/publish_package.json`
```json
{
  "title": "霸道总裁的秘密",
  "description": "...",
  "hashtags": ["#都市甜宠", "#霸道总裁", "#漫剧"],
  "coverDescription": "..."
}
```

---

## 四、项目文件结构

每个项目的所有中间产物都保存在 MinIO 中：

```
projects/{项目ID}/
├── 01-script/
│   ├── script_v1.json / script_v1.txt
├── 02-characters/{角色名}/
│   ├── design.json / portrait.png / threeview.png
│   └── expressions/{neutral,happy,sad,surprised,angry}.png
├── 03-storyboard/
│   ├── storyboard.json
│   ├── backgrounds/scene_0X.png
│   ├── frames/{NNN}/frame_data.json
│   └── prompts/frame_{NNN}.txt
├── 04-voice/
│   ├── voice_config.json
│   └── audio/frame_{NNN}.mp3
├── 05-video-clips/
│   └── clips/frame_{NNN}.mp4
├── 06-final/
│   ├── output.mp4 / subtitles.ass / cover.png
│   └── publish_package.json
└── workflow.json
```

---

## 五、常见问题

### Q: 步骤2 剧本生成失败怎么办？
点开步骤详情查看错误。通常是 JSON 格式校验失败，系统已自动重试 3 次。可点"重试"按钮再试。

### Q: 步骤3 角色立绘不满意？
表情图默认以三视图为参考图生成，如果效果不好，可修改 `CharacterService` 中的生成 prompt。

### Q: 步骤6 某个帧的视频片段一直失败？
- 检查 prompt 是否合理（太模糊/太短的 prompt 生成效果差）
- 编辑 prompt 增加细节（角色外貌、表情、动作、光线）
- 可点"跳过"让该帧使用静态背景+配音，不影响整集

### Q: 步骤6 帧之间角色长相不一致？
每帧生成时都传了同一个 `threeview.png` 作为参考图。如果不一致，可能是视频生成模型对参考图的理解偏差。建议调整 prompt，更精确地描述角色外貌。

### Q: edge-tts 报错？
确保系统安装了 edge-tts：
```bash
pip install edge-tts
```

### Q: 如何修改角色的 TTS 语音？
编辑 `.env` 中的 `TTS_VOICE`，或在 `Application.yml` 中修改。后续版本支持在项目页面直接为每个角色选择不同语音。

---

## 六、环境配置速查

| 配置项 | .env 变量 | 默认值 | 用途 |
|--------|----------|--------|------|
| 主 LLM | `LLM_API_KEY/BASE/MODEL` | `deepseek-chat` | 剧本/人设/分镜/文案 |
| 视觉 LLM | `LLM_VISION_API_KEY/BASE/MODEL` | `qwen-vl-plus` | 爆款视频分析（可选） |
| 文生图 | `IMAGE_GEN_API_KEY/BASE/MODEL` | `wanx-v1` | 角色立绘/背景图 |
| 图生视频 | `VIDEO_GEN_API_KEY/BASE/MODEL` | `wan2.7-i2v` | AI 视频片段生成 |
| OSS 存储 | `OSS_ENDPOINT/ACCESS_KEY_ID/ACCESS_KEY_SECRET/BUCKET_NAME` | - | 公网图片存储（视频生成需要） |
| TTS | `TTS_PROVIDER/VOICE` | `edge/zh-CN-XiaoxiaoNeural` | 配音 |
| 数据库 | `DB_USERNAME/PASSWORD` | `admin/password` | PostgreSQL |
| 对象存储 | `MINIO_ENDPOINT/ACCESS_KEY/SECRET_KEY` | `localhost:9000` | MinIO 本地存储 |

### OSS 配置说明

Wan2.7 图生视频需要公网可访问的图片 URL，因此需要配置阿里云 OSS：

1. 创建 OSS Bucket，**读写权限**选择**公共读**
2. **不要勾选**"阻止公共访问"
3. 在 `.env` 中填入：
   ```
   OSS_ENDPOINT=oss-cn-beijing.aliyuncs.com    # 地域节点（不含 bucket 名）
   OSS_ACCESS_KEY_ID=LTAI5t...                  # RAM 用户 AccessKey
   OSS_ACCESS_KEY_SECRET=...                    # RAM 用户 Secret
   OSS_BUCKET_NAME=movie-create                 # Bucket 名称
   ```
4. RAM 用户需要 `oss:PutObject` 权限

未配置 OSS 时，图片仍会存储到 MinIO，系统降级运行但视频生成功能不可用。

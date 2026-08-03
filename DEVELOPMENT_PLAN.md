# RegionStory 实施方案与完成标准

## 固定基线

- Minecraft 1.21.11
- Java 21
- Fabric Loader 0.19.3+
- Fabric API 0.141.6+1.21.11
- 仅依赖 Fabric API；不引入 NanoVG、Cloth Config 或其他原生 UI 后端

## 实施顺序

### 1. 自定义 GUI Shader 后端

- `RegionStoryPipelineRenderer` 注册四个无纹理 RenderPipeline：开放渐隐面板、独立胶囊、底部通栏和符号。
- `RegionStoryPanelRenderState` 与 `RegionStorySymbolRenderState` 只提交几何、颜色和局部坐标。
- `regionstory_panel.fsh` 使用 SDF、`smoothstep` 和 `fwidth` 绘制左半圆、中间可拉伸主体、右侧渐变透明未封口边缘。
- `regionstory_symbol.fsh` 绘制箭头、菱形、聊天气泡、星形、地图标记和装饰线。
- Shader 注册失败时停用 RegionStory 面板，不回退到 Minecraft 原生矩形绘制。

### 2. 对话与提示布局

- 选项框左边界固定在屏幕约 60% 位置。
- 选项框高度默认 20，长文本按字体实际宽度换行并垂直居中。
- 区域提示位于屏幕中心偏右下方，F 键胶囊位于提示框外部左侧。
- 底部对话通栏使用上下渐变透明，不调用背景模糊。
- 说话人、头衔、装饰线和继续菱形沿屏幕中轴线排列。

### 3. 字体与动效

- `dialogue.ttf` 和 `dialogue.json` 由 Minecraft `TextRenderer` 加载。
- 悬停状态使用 20 tick 正弦亮度循环。
- 点击状态使用 3 tick 快速高亮后恢复，并在动画结束后提交选项。
- 对话通栏和选项列表使用 12 tick 入场淡入/上移。

### 4. 服务端剧情系统

- 每 5 个服务端 tick 检测玩家所在区域。
- 支持 `sphere` 和 `box` 两种区域。
- 服务端校验区域、对话、当前条目和选项索引。
- `commands[]` 始终由服务端以玩家身份执行。
- 选项支持 `next` 跳转、`commands[]` 执行和 `endDialog` 显式结束。
- 数据包重载时校验 ID、坐标、跳转和区域-对话引用，并关闭旧会话。
- `/regionstory reload` 需要 OP 2。

### 5. 镜头与输入

- 对话开始时从原视角平滑移动到玩家右后方第三人称。
- 对话结束时反向移动并恢复原视角。
- 对话期间锁定游戏鼠标，隐藏准星、快捷栏等原版 HUD。
- 转场时长、距离、偏移角度和高度写入 `config/regionstory.json`。

## 数据文件

```text
src/main/resources/data/regionstory/regions/*.json
src/main/resources/data/regionstory/dialogues/*.json
```

示例：

- `starfall_circle.json`：圆形区域。
- `windmill_box.json`：长方体区域。
- `starfall_intro.json`：带两个分支的示例对话。
- `windmill_story.json`：带两个分支的示例对话。

## 完成验收

实现全部代码和资源后统一执行：

```powershell
./gradlew.bat clean build --offline
./gradlew.bat runClient
```

客户端需要验证区域进入/离开、F 键、提示点击、选项点击、中文长文本、悬停循环、点击动画、镜头进出、GUI Scale、多分辨率和 `/regionstory reload`。

## 发布

`.github/workflows/build-release.yml` 支持手动触发和 `v*` 标签触发，构建 `build/libs/regionstory-*.jar`，并在标签发布时自动附加 JAR 到 GitHub Release。

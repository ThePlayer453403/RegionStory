# RegionStory

Fabric 模组，为 Minecraft 1.21.11 提供数据包驱动的区域剧情对话系统，目标是实现类似原神的区域触发、分支对话、选项交互和镜头转场。

## 功能

- `sphere` 圆形区域和 `box` 长方体区域。
- 服务端权威的区域检测、对话状态、分支跳转和命令执行。
- 区域提示条：可配置提示文字、图标和可绑定按键，默认按 `F`。
- 底部全屏渐变对话框：说话人、身份标签、正文和继续菱形指示器居中显示。
- 分支选项：图标、自动换行、鼠标悬停高亮、点击反馈和目标条目跳转。
- 对话开始/结束时的第一人称与第三人称镜头转场。
- `/regionstory reload` 热重载数据包对话和区域定义。
- 使用 `assets/regionstory/font/dialogue.ttf` 的自定义字体。

## 环境要求

- Minecraft `1.21.11`
- Java `21`
- Fabric Loader `0.19.3` 或更高
- Fabric API `0.141.6+1.21.11` 或兼容版本

本项目只依赖 Fabric API，不要求 Cloth Config。

## 构建

Windows：

```powershell
./gradlew.bat build
```

Linux/macOS：

```bash
./gradlew build
```

构建产物位于 `build/libs/regionstory-<version>.jar`。当前版本号在 `gradle.properties` 的 `mod_version` 中定义。

## 安装

1. 安装与 Minecraft `1.21.11` 对应的 Fabric Loader。
2. 将 Fabric API 放入实例的 `mods` 文件夹。
3. 将构建出的 `regionstory-*.jar` 放入同一个 `mods` 文件夹。
4. 启动游戏。首次进入客户端后会生成 `config/regionstory.json`。

## 第一个示例

项目自带两个示例：

- `starfall_circle`：圆形区域，关联 `starfall_intro` 对话。
- `windmill_box`：长方体区域，关联 `windmill_story` 对话。

示例文件位于：

```text
src/main/resources/data/regionstory/regions/
src/main/resources/data/regionstory/dialogues/
```

数据包命名空间为 `regionstory`。将自定义文件放入世界数据包的同名目录即可覆盖或增加内容。

## 区域 JSON

区域文件路径：`data/<namespace>/regions/<file>.json`

圆形区域示例：

```json
{
  "id": "starfall_circle",
  "dimension": "minecraft:overworld",
  "type": "sphere",
  "center": [0, 64, 0],
  "radius": 8,
  "prompt": "按 F 对话",
  "icon": "regionstory:textures/gui/dialogue_icon.png",
  "dialogue": "starfall_intro",
  "priority": 10
}
```

长方体区域示例：

```json
{
  "id": "windmill_box",
  "dimension": "minecraft:overworld",
  "type": "box",
  "min": [-8, 63, -8],
  "max": [8, 72, 8],
  "prompt": "按 F 与守望者交谈",
  "dialogue": "windmill_story",
  "priority": 5
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `id` | 区域唯一 ID。 |
| `dimension` | 维度 ID，例如 `minecraft:overworld`。 |
| `type` | `sphere` 或 `box`。 |
| `center` / `radius` | `sphere` 使用的中心坐标和半径。 |
| `min` / `max` | `box` 使用的两角坐标。 |
| `prompt` | 区域提示文字，不需要重复写按键名。 |
| `icon` | 可选图标资源 ID。 |
| `dialogue` | 触发的对话 ID。 |
| `priority` | 重叠区域的优先级，数值越大越优先。 |

## 对话 JSON

对话文件路径：`data/<namespace>/dialogues/<file>.json`

```json
{
  "id": "starfall_intro",
  "start": "start",
  "entries": [
    {
      "id": "start",
      "speaker": "派蒙",
      "speakerTitle": "旅行伙伴",
      "text": "前面就是星落湖了，要不要先去看看？",
      "options": [
        {
          "text": "当然，出发吧。",
          "next": "lake",
          "commands": [],
          "icon": "compass"
        },
        {
          "text": "我们先休息一下。",
          "next": "rest",
          "commands": [],
          "icon": "dialogue"
        }
      ]
    },
    {
      "id": "lake",
      "speaker": "旅行者",
      "speakerTitle": "旅人",
      "text": "湖面比想象中还要平静。",
      "next": "finish",
      "commands": []
    },
    {
      "id": "finish",
      "speaker": "派蒙",
      "speakerTitle": "旅行伙伴",
      "text": "那就这样说定了。",
      "endDialog": true,
      "commands": []
    }
  ]
}
```

Entry 字段：

- `id`：条目唯一 ID。
- `speaker`：说话人名称。
- `speakerTitle`：可选身份标签，会显示在名字下方。
- `text`：正文，超过宽度会自动换行。
- `options`：可选分支列表。
- `next`：无选项时的下一条目 ID。
- `commands`：进入该条目后由服务端以玩家身份执行的命令列表，可省略 `/`。
- `endDialog`：设为 `true` 时结束对话。

Option 字段：

- `text`：选项文字。
- `next`：点击后跳转的条目 ID；为空时结束对话。
- `commands`：选择后执行的服务端命令。
- `icon`：内置图标名或自定义 PNG 资源 ID。

内置图标包括 `star`、`compass`、`map`、`dialogue` 和 `exit`。

## 命令和按键

- `/regionstory reload`：需要 OP 2，热重载服务端区域和对话数据。
- 默认对话键：`F`，可以在 Minecraft 控制设置中改键。
- 无选项台词：点击对话框或按任意键推进。
- 有选项台词：鼠标点击选项，或使用鼠标悬停后点击。
- `Esc`：结束当前对话。

## 镜头配置

客户端首次启动时生成 `config/regionstory.json`。可调整：

- `enterDuration`：进入对话的转场时长。
- `exitDuration`：退出对话的转场时长。
- `thirdPersonDistance`：第三人称镜头距离。
- `heightOffset`：镜头高度偏移。
- `yawOffset`：镜头水平偏移角度。
- `pitchOffset`：镜头俯仰偏移角度。

对话期间会锁定移动输入，隐藏准星和背包 HUD；不会对游戏背景添加模糊效果。

## 贴图和字体

GUI 贴图目录：

```text
src/main/resources/assets/regionstory/textures/gui/
```

当前三段式素材为：

- `dialogue_left.png`：左侧边缘。
- `dialogue_middle.png`：中间可横向拉伸区域。
- `dialogue_right.png`：右侧渐变透明区域。

区域提示条和分支选项会复用这组三段式素材。替换 PNG 后重新执行 `build` 即可生效，建议使用整数倍尺寸和清晰的像素边缘。

字体文件和定义位于：

```text
src/main/resources/assets/regionstory/font/dialogue.ttf
src/main/resources/assets/regionstory/font/dialogue.json
```

替换 `dialogue.ttf` 后重新构建，即可同时替换对话框、选项和区域提示文字的字体。

## 项目结构

```text
src/main/java/com/regionstory/
  client/       客户端 HUD、按键、Screen 和镜头控制
  data/         区域、对话定义和服务端会话
  mixin/        相机与输入相关 Mixin
src/main/resources/
  data/         示例区域和对话 JSON
  assets/       字体、语言文件和 GUI 资源
```

## 开发说明

项目使用 Fabric Loom 和 Java 21。修改 Java 或资源后运行：

```powershell
./gradlew.bat build
```

开发客户端可以使用：

```powershell
./gradlew.bat runClient
```

数据包 JSON 解析错误会写入日志并跳过坏文件，不会因为单个文件导致服务器崩溃。

## 许可证

本项目使用 MIT License，详见 [LICENSE](LICENSE)。

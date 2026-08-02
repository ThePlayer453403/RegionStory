# RegionStory 开发记录

## 当前基线

- Minecraft 1.21.11
- Java 21
- Fabric Loader 0.19.3
- Fabric API 0.141.6+1.21.11
- Fabric Loom Remap 1.17-SNAPSHOT

## 已实现

- sphere / box 区域 JSON
- 区域优先级
- 服务端区域轮询
- RegionHint Payload
- 服务端权威 DialogueSession
- 对话推进与选项 Payload
- 服务端命令执行
- `/regionstory reload`
- HUD 提示条和点击触发
- 原神风格对话 Screen
- 客户端镜头转场控制器与 Camera Mixin
- `config/regionstory.json` 镜头配置

## 后续验证顺序

1. 解析 Gradle 与 1.21.11 mappings/API
2. 修复 1.21.11 映射差异
3. 启动开发客户端验证区域提示
4. 验证服务端对话和分支
5. 验证命令权限和数据包热重载
6. 验证镜头 Mixin 与退出恢复
7. 构建最终 JAR

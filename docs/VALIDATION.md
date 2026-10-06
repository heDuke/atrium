# 修复验证记录与设备验收

日期：2026-10-06。基线：`768f1fc`。

## 已执行

- `git diff --check`：通过，无补丁空白错误。
- `bash scripts/check-no-horologist.sh`：通过。
- 所有 15 个资源/Manifest XML：Python XML parser 解析通过。
- 各 app/feature 模块 Kotlin 引用的 `R.string`：对应模块资源均存在。
- Manifest 检查：同一 MainActivity 注册 MAIN/HOME/DEFAULT 和 MAIN/LAUNCHER；singleTask。
- 修改后源码复核：引导持久化先于导航；应用启动处理两类预期异常；
  PackageManager 查询在 IO scope；隐藏应用恢复入口；Home/普通入口区分根返回；
  系统输入取消保留关键词；操作页位于独立导航目的地。

以上是本地静态检查；云端编译验证见下节，UI 运行仍未验收。

## 本地构建限制与云端验证

尝试命令：

```bash
bash gradlew :app:assembleDebug :app:assembleRelease :core:data:testDebugUnitTest :app:lintDebug --no-daemon
```

Gradle wrapper 下载 `gradle-9.7.0-bin.zip` 时返回 `java.net.SocketException: Network is unreachable`。
本环境也没有 Android SDK 或模拟器，因此本地 Kotlin 编译、Gradle 单元测试、Lint、APK 安装
以及 UI 截图/录屏均未完成。后续已接入 GitHub Actions；云端验证不受这个本地限制。

新增 4 个 AppCatalogRules JUnit 用例，覆盖：隐藏不泄漏及恢复、置顶/收藏排序、
去空白且忽略大小写的搜索、同包多 Activity 的组件身份和包级隐藏。
保留既有 12 个性能/减弱动态状态转换用例。

2026-10-06 云端运行 [37485860336](https://github.com/heDuke/atrium/actions/runs/37485860336)：
`testDebugUnitTest` 已完成 Debug 源码编译，16 个用例全部通过，无失败、错误或跳过；
`:app:lintDebug` 通过，无错误、38 条警告（依赖更新提示、资源、备份配置、Wear Recents 等）。
该轮整体失败是显式 SDK 安装命令找不到 `platforms;android-37`，APK 步骤因此被跳过。
工作流已修正为由 AGP 根据 compileSdk 解析平台包，环境初始化成功后才运行后续检查。

修正后运行 [37486525986](https://github.com/heDuke/atrium/actions/runs/37486525986)
已成功执行 `:app:assembleDebug :app:assembleRelease`，并上传 Debug APK 和未签名 Release APK。
Debug APK 可用于设备测试；Release APK 仍须签名后安装。

## Pixel Watch 3 待验收（未执行）

GitHub Actions 新增 `Android build` 工作流，自动编译 Debug/未签名 Release、运行所有
Debug 单元测试及 `:app:lintDebug`，并保留 APK 和检查报告 14 天。支持 main 推送、PR
和手动触发。AGP 更新到 SDK 37 要求的最低补丁版本 9.1.1，移除不支持 SDK 的警告抑制。
CI 是否通过应以对应提交的 Actions 运行结果为准；配置工作流本身不代表已通过编译。

| 流程 | 预期 |
|---|---|
| 普通应用入口 | 初次引导；完成后重启进入抽屉；根返回可退出 |
| Home 角色支持 | 授权成功显示默认状态；拒绝后抽屉仍可用 |
| Home 角色不开放 | 不出现授权按钮，显示设备限制说明 |
| 重复 Home 请求 | 复用 Activity，从子页面回抽屉，不堆叠界面 |
| Home 根返回 | 停留抽屉，子页面返回先回上一页 |
| 物理表冠/按键 | 实测路由；不假定系统一定转发给第三方 Home |
| Tile 入口 | 从已有子页面重新打开时进入抽屉 |
| 安装/卸载/禁用/更新 | 返回应用后目录更新，图标同步，失效启动不崩溃 |
| 收藏/置顶/隐藏/恢复 | 状态同步，隐藏后可恢复，卸载重装后偏好保留 |
| 系统输入 | 完成更新关键词，取消保持原关键词，清除恢复全部可见应用 |
| 系统旋转输入 | 列表可正常滚动，系统输入关闭后焦点/旋转输入可用 |
| 动效档位 | 减弱动态锁定省电档；关闭恢复上一档；标准动效不等于完全无动画 |
| 写入失败/慢写入 | 引导不提前跳转；设置失败显示提示，不冒充保存成功 |
| 重建/进程恢复 | 操作目标、搜索词和导航状态可恢复 |

## 布局与无障碍待验收

小圆屏、大圆屏、方屏；默认/放大字体；长中文名称及中英文混合名称。
检查列表顶部/中部/底部的变形、文字裁切、48dp触控、TalkBack 状态和自定义操作。
Grid 按整行缩放/淡出，内部按钮不单独再变形；具体圆屏裁切须通过实际截图确认。

## 有意保留的范围

- WFF 和 seed-color 仍为明确记录的 stub，不声称已交付表盘或种子生成配色。
- Tile 仍为打开抽屉的快捷入口，不新增收藏捷径。
- 省电/性能档位控制动效及 Tile 刷新提示，不保证测得的电池节省或硬件性能变化。
- 未安装应用的隐藏/收藏/置顶记录保留，隐藏管理只显示仍安装的应用。

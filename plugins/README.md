# 阅读 NG 宠物 SDK 2.1.0

一个作者工程包含一份 pet.json、Kotlin 或 Java 源码，以及可选的原始素材。构建工具会由 pet.json 生成包内运行清单及可选展示信息，检查编译后的入口，输出可本地导入或上架的单层 ZIP。

## 获取 SDK 和创建项目

在阅读工程中用 Windows PowerShell 生成 SDK。发布给作者的是 build/outputs/pet-sdk/pet-sdk-2.1.0.zip，内含固定接口 JAR、打包工具、Gradle wrapper 和光球模板。阅读 App 的构建仅依赖基础接口模块。

    $env:GRADLE_USER_HOME = 'D:\code\.gradle-legado'
    .\gradlew.bat :modules:pet-api:exportPetSdk --no-parallel

以下步骤在作者机器上执行。安装 JDK 17 和带 android-36 平台的 Android SDK，将 ANDROID_HOME 指向其目录。把 SDK 解压到工具目录，将 template 复制为独立的插件工程。

    Expand-Archive -LiteralPath .\pet-sdk-2.1.0.zip -DestinationPath .\pet-sdk
    Copy-Item -LiteralPath .\pet-sdk\template -Destination .\my-orb -Recurse
    $builder = (Resolve-Path .\pet-sdk).Path
    $pet = (Resolve-Path .\my-orb).Path
    & "$builder\gradlew.bat" -p $builder "-PpetProject=$pet" packagePet --no-parallel

产物位于 my-orb/build/outputs/pet/orb-1.zip。构建在插件工程自己的 build 目录写入临时文件与产物。插件工程可以放在任意目录，不需要阅读 App 源码。Unix 下使用同一 SDK 中的 gradlew。

## 插件配置和接口

| 字段 | 约定 |
| --- | --- |
| id | 同一插件的稳定身份。小写字母开头，随后可用小写字母、数字和下划线，总长不超过 64。none 与 button 为保留值 |
| name | 安装后显示的名称 |
| versionCode | 每次发布新包递增的整数，须与目录版本一致 |
| entryClass | 实现 io.legado.pet.api.PetPlugin 的公开类的 JVM 二进制名称，例如 example.OrbPlugin 或 example.Factory$Orb。入口提供公开无参构造函数，其 id 与清单相同 |
| sharedSources | 可选的作者共享源码相对目录，由这些源码编译出的角色逻辑归插件所有 |
| info | 可选的离线展示信息对象，字段和限制见下节 |

SDK 工具固定输出 schemaVersion=2、kind=pet、apiVersion=2。包内携带插件自己的 Kotlin 运行依赖。发布校验检查入口实现、主应用内部类型引用和重复打入的 SDK 类。插件可按照需要在 src/main/assets/ 下放置文件，素材保留作者提供的字节。

PetPlugin.createSession 在后台准备资源。宿主将当前会话移交给界面线程后，以视口 dp 派发绘制和输入。会话自行绘制、计算命中并决定下帧时间，release 返回 true 表达激活意图，聊天导航归宿主负责。宿主隐藏窗口时暂停驱动并通知 setVisible，切换后调用 close 释放资源。saveState 可返回最多 4096 字节的角色私有数据，自行维护数据版本并处理空数据或不支持的版本。

## 离线展示信息

SDK 2.1.0 继续使用 API 2 和 `pet-api-2.0.0.jar`，没有新增运行接口。可在作者配置 `pet.json` 中加入可选 `info` 对象：

```json
{
  "id": "my_pet",
  "name": "我的宠物",
  "versionCode": 1,
  "entryClass": "example.pets.MyPetPlugin",
  "info": {
    "versionName": "1.0",
    "author": "作者名称",
    "description": "宠物的简短介绍。",
    "license": "MIT",
    "preview": "preview/pet.png"
  }
}
```

这些字段全部可选；省略 `info` 时仍可打包和导入。工具会生成独立的 `assets/pet-info.json`，内容为 `schemaVersion: 1` 加上述展示字段，`assets/plugin.json` 的运行字段保持不变。展示版本不参与身份或更新判断，安装比较始终使用 `id`、`versionCode` 和包摘要。作者自报信息也不证明可信来源。

| 展示字段 | 约定 |
| --- | --- |
| versionName | 展示版本，最多 64 个 UTF-16 字符 |
| author | 作者，最多 128 个 UTF-16 字符 |
| description | 描述，最多 4096 个 UTF-16 字符，可含换行和 Tab |
| license | 许可标识或简短说明，最多 256 个 UTF-16 字符 |
| preview | 相对 `src/main/assets/` 的 PNG 路径，最多 240 个字符，例如 `preview/pet.png` |

提供的文本须为非空字符串，不允许控制字符（描述中的换行、回车和 Tab 除外）。展示 JSON 使用 UTF-8，最多 32 KiB。预览必须是包内普通 PNG 文件，大小不超过 1 MiB，宽高各为 1～1024 像素，总像素不超过 1024²。工具检查 PNG 头、编码参数、各数据块 CRC、IDAT 和文件结束，不通过运行宠物代码生成预览。

预览路径不接受外部 URL、绝对路径、反斜杠、空路径段、`.` 或 `..`。素材目录及文件禁止符号链接或重定向目录；作者不得手写 `src/main/assets/plugin.json` 或 `pet-info.json` 覆盖生成资料。打包保留源图片字节，最终 ZIP 再次校验展示信息和预览。缺少展示资料的包由宿主显示清单名称、内部版本和占位图，不联网补齐。

开发者可通过“打包 ZIP → 宠物管理导入 → 确认同版本替换”验证本地迭代，不必为每次调试递增版本；正式发布新内容仍须递增 `versionCode`。导入后显式使用，静态浏览和确认不会执行插件代码。

## 验证独立更新

模板的第一版使用 Canvas 绘制光球，支持拖动、点击及位置恢复。验证第二版时，在复制出的作者工程中应用 SDK 内的 examples/orb-v2.patch，再次执行相同的 packagePet 命令。补丁同时修改实际动作和发布版本，生成 orb-2.zip，拖动释放后产生逐渐消退的光晕。两版使用相同的插件 ID 和状态格式。该示例供开发和验收使用。

    Push-Location $pet
    git apply "$builder\examples\orb-v2.patch"
    Pop-Location
    & "$builder\gradlew.bat" -p $builder "-PpetProject=$pet" packagePet --no-parallel

## 两只已有宠物

本仓库的 pet-gugugaga、pet-blue-fish 都是独立作者工程，共用的旧角色绘制与动画工具位于 pet-layered/src/main/java。以这两个工程各自的绝对目录作为 petProject，就能使用与模板相同的 SDK 命令构建 gugugaga-4.zip 与 blue_fish-3.zip。

咕嘎 2.2（内部版本 4）和蓝色大肥鱼 2.1（内部版本 3）新增包内展示版本、作者、介绍与静态预览。预览直接复制已有透明详情 PNG；咕嘎沿用已恢复的 13 张原始 PNG，蓝鱼沿用已有角色素材，均保留原文件字节，不做 Q95 重编码。两包接口仍为 API 2。

资源中心按 pet 类型、包内身份、摘要和接口版本校验这些 ZIP。下载或本地导入后由用户显式启用。插件与宿主同进程运行并含可执行代码，导入时应确认来源可信。

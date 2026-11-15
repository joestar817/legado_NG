# 雨夜主题算法回归

`RainNightProfileTest.java` 保留原宿主 `ListeningMotionConfigTest` 对雨夜已验收参数、四层雨线数量、雾/叶/水滴数量和水滴周期的断言；另检查着色器哈希表容量、全部取值范围与确定性。不复制算法，不依赖宿主配置枚举，也不创建 Android/GL 对象。

在主题生产源码已编译后，Windows PowerShell 可运行以下独立 JVM 检查。`$themeClasses` 指向本次编译的生产 class 根目录；Gradle 作者项目通常为 `build\tmp\kotlin-classes\release`，诊断编译也可提供等价路径。

```powershell
$themeClasses = 'D:\themes\rain-night\build\tmp\kotlin-classes\release'
$kotlinStdlib = 'D:\tools\kotlin-stdlib-2.3.21.jar' # 使用作者构建匹配的 Kotlin 标准库
javac -encoding UTF-8 -classpath $themeClasses -d build\profile-tests tests\RainNightProfileTest.java
java -classpath "build\profile-tests;$themeClasses;$kotlinStdlib" io.legado.themes.rain_night.RainNightProfileTest
```

预期输出：`RainNightProfileTest: 3 cases, 12353 assertions passed`。此测试只证明 profile 算法和已验收参数，渲染、动画连续性、加载与释放仍需 Android 实际验证。`tests` 不进入主题 ZIP。
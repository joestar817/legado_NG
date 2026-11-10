# NG 卡通动态主题素材说明

本目录保存“湖畔樱花”“好奇猫咪”两套动态主题及播放器可选“雨夜”动效共用的场景素材。
三套场景仅用于阅读 NG 免费开源项目，不用于素材销售；播放器与主题运行时均为本项目自行实现。

## 湖畔樱花

- 来源：[Wallpaper Engine Workshop 3056182945「樱花」](https://steamcommunity.com/sharedfiles/filedetails/?id=3056182945)
- `sakura/background.webp`：从原场景背景固定裁出竖屏双树取景，经过 CUNet 超分并编码为 WebP。
- `sakura/water_normal.png`、`sakura/water_phase.png`：用于湖面局部折射的辅助纹理。
- 花瓣、密度、轨迹和 30 FPS 调度由本项目程序化实现；不包含原音乐、完整场景包或 Wallpaper Engine Runtime。

## 好奇猫咪

- 来源：[Wallpaper Engine Workshop 3455074362「4K Curious Cats (PHONE)」](https://steamcommunity.com/sharedfiles/filedetails/?id=3455074362)
- `cats/background.webp`、`canopy.webp`、`black_cat.webp`、`white_cat.webp`、`fence.webp`、
  `paws.webp`、`foreground.webp`：七个原场景图层经 CUNet 无降噪 2× 超分并编码为无损 WebP。
- `cats/scene.catv`：本项目固定 `CATV0001` 协议，保存七层几何与原 Puppet 动画烘焙矩阵；App 不解析 MPKG、MDL、任意 Shader 或脚本。
- `cats/poster.webp`：从相同场景时序的第 0 帧生成；两只猫已完全缩回栅栏，只保留爪子，作为静态主题封面和失败回退图。

## 雨夜

- 来源：[Wallpaper Engine Workshop 3503882817「Convenience Store in the Rain」](https://steamcommunity.com/sharedfiles/filedetails/?id=3503882817)
- `rain_night/background.webp`：参考原场景重新组织为 9:20 竖屏构图并经过 CUNet 超分。
- 暴雨、水滴、雾气、落叶、湿光和 20 秒循环均由本项目程序化实现；不包含原雨声音频、日期、时钟、音频柱、完整场景包或通用 Shader Runtime。

## 联系与删除

原作品著作权归各自作者所有。如权利人认为相关使用不当，请通过项目 Issue 或 README
中的交流渠道联系我们，并附上可核对的作品或权利信息；项目维护者会及时删除或替换相关素材。

# 背景音乐平台标识来源

这些图片仅用于在背景音乐的音乐库入口中识别对应平台。品牌名称、商标和标识的权利分别归网易云音乐、腾讯及其相应权利人所有；官方公开提供图片不等于授予开源许可。本文件记录素材来源，不将这些标识声明为本项目原创或适用本项目代码许可证，也不表示本项目获得平台官方认可。

获取日期：2026-10-20。

## 网易云音乐

- 应用资源：`app/src/main/res/drawable-nodpi/bgm_platform_netease.png`
- 来源页面：[网易云音乐官网](https://music.163.com/)。
- 页面依据：首页 `meta itemprop="images"` 指向网易音乐图片 CDN 的该品牌图。
- 原始图片：[200 × 200 JPEG](https://p3.music.126.net/9z9CeujRSPOPm7Rq2DFw_g==/6674035581283071.jpg?param=200y200)。官网元信息中的原始链接使用 HTTP，下载使用相同主机和路径的 HTTPS 地址。
- 处理方式：使用 Pillow 将 JPEG 解码后保存为 PNG，保留 200 × 200 尺寸和解码后的颜色像素；未裁剪、重绘、改色或生成品牌形状。PNG 转换不会消除原始 JPEG 已有的压缩痕迹。
- PNG SHA-256：`045871E9035C8AFC4A9AB7B61BD95D9AAE498D6059C070FD7660EB6086CC350C`。

## QQ 音乐

- 应用资源：`app/src/main/res/drawable-nodpi/bgm_platform_qq.png`
- 来源页面：[QQ 音乐移动官网](https://i2.y.qq.com/n3/other/pages/myqq/index.html)，以移动端 User-Agent 访问。
- 页面依据：顶部平台标识 `top_operation_bar__media--logo` 的图片地址。
- 原始图片：[官方移动端 SVG](https://y.qq.com/mediastyle/mod/mobile/img/logo.svg?max_age=2592000)。
- 处理方式：使用 CairoSVG 将官方 58 × 58 SVG 按 4 倍比例栅格化为 232 × 232 RGBA PNG，保留原始路径、黄色背景、绿色渐变音符和透明区域；未裁剪、重绘、改色或生成品牌形状。
- PNG SHA-256：`B3ED93236235DD148ACDE2C095E4597C0EF17A3608626EAD9BAA3842C6632DAD`。

两个 PNG 均放在 `drawable-nodpi` 中，由界面在既定图标尺寸内等比例显示。界面不得应用单色 tint 覆盖品牌原色。

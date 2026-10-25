# BGM 搜索加载动画来源

- 图标集：[SVG Spinners](https://icon-sets.iconify.design/svg-spinners/)，作者 Utkarsh Verma。
- 选用：[3-dots-fade](https://icon-sets.iconify.design/svg-spinners/3-dots-fade/)。
- 参考源码：[svg-css/3-dots-fade.svg](https://github.com/n3r4zzurr0/svg-spinners/blob/main/svg-css/3-dots-fade.svg)。
- 许可：[MIT](https://github.com/n3r4zzurr0/svg-spinners/blob/main/LICENSE)，原文随应用保存在 `app/src/main/assets/licenses/svg-spinners-MIT.txt`。

`BgmSearchLoadingIndicator` 用 Compose Canvas 绘制同样的三点渐隐：24×24 坐标，圆心x为4／12／20、y为12，半径3；800ms循环，在750ms线性衰减至20%透明度，随后保持至本周期结束，三点错开150ms。颜色读取NG主题主色，墨水屏静态显示，不引入SVG运行时或网络加载。

在线曲库搜索与歌单详情采用同一容器内动画：初次加载／刷新位于内容区，歌单详情位于封面信息的分隔线下方；追加分页位于列表尾部。账号资料读取也复用此组件。歌单根列表和其它页面不更换公共加载样式。

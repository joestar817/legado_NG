# 宠物静态 DEX 测试夹具

这些夹具由旁边的 `sources/` Java 源码通过 JDK 17 javac（source/target 8）与 Android Build Tools 36.0.0 的 D8（`--min-api 21 --release`）生成。API stub 仅参与编译，正常夹具不携带宿主接口类。它们不经 ClassLoader 加载，不安装或运行；`ThrowingPet` 的构造器故意抛异常，用来表明静态检查不执行构造。

- `minimal.dex`：PetEntry 及私有/有参构造、抽象、无关、嵌套和构造抛异常的入口。
- `cross-base.dex`、`cross-entry.dex`：继承链跨三个 DEX。
- `sdk-shadow.dex`：故意携带 PetPlugin API 定义，必须拒绝。
- `host-reference.dex`：故意引用未随包提供的 App 内部类型，必须拒绝。

校验范围遵循 [AOSP DEX 格式](https://source.android.com/docs/core/runtime/dex-format)。这些测试验证结构和入口 ABI，不能替代 ART 指令验证或运行时验收。

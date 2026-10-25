# 自编译发布签名信息（fork 专用）

本仓库自编译 APK 使用的签名证书信息。**私钥不入库**，本文只记录公开证书与指纹，
用于日后核对「这个 APK 是不是我这条签名线编译的」。

## 当前签名（NG 本地签名 v1）

| 项目 | 值 |
| --- | --- |
| keystore 路径 | `app/legado.jks`（已被 `.gitignore` 忽略，**不要提交**） |
| 密钥别名 | `legado-ng` |
| 证书主体 / 签发者 | `CN=Legado NG Local, OU=Dev, O=tj5332888, L=Local, ST=Local, C=CN` |
| 算法 | RSA 2048 位，签名 SHA256withRSA |
| 有效期 | 2026-10-25 ~ 2054-03-12（10000 天） |
| SHA-256 指纹 | `7F:76:71:5E:D8:08:80:A1:B5:BE:3E:C5:4D:95:9B:3C:89:B4:A3:7C:F1:72:81:40:FD:73:5A:57:06:E7:C1:72` |
| SHA-1 指纹 | `AF:99:2E:70:0D:6F:7E:71:75:76:C8:7C:E9:F7:5D:B9:9D:B4:51:97` |
| MD5 指纹 | `6E:69:C8:26:AF:3D:D6:76:B7:1B:B6:B4:07:E0:C8:DA` |
| 公钥证书 | [`legado-ng-release-cert.pem`](legado-ng-release-cert.pem) |
| 首个使用签名的版本 | `3.26.102600`（versionCode 10086），包名 `io.legado.app.ngx.release` |
| 签名方案 | V1 + V2 + V3 + V4 |

核对命令：

```
apksigner verify --print-certs <apk>
openssl x509 -in docs/legado-ng-release-cert.pem -noout -fingerprint -sha256
```

## 历史签名（已废弃，无法覆盖升级）

| 项目 | 值 |
| --- | --- |
| 用于版本 | `3.26.101513` ~ `3.26.101610` 等自编译包 |
| 证书主体 | `CN=legado, OU=legado, O=legado, L=legado, ST=legado, C=legado` |
| SHA-256 指纹 | `0D:85:F3:B1:B1:B5:3F:1C:CE:2C:0D:19:AF:B7:5E:72:F7:F5:C3:95:27:12:85:84:78:EF:05:51:7C:3E:72:C1` |
| 状态 | **原始 keystore 已丢失**，本机全盘搜索无果。APK 只含公钥，私钥不可反推，此签名线永久终止。 |

上游官方发布使用另一套签名（`io.legado.app.ng.release` 包名），与本 fork 的
`io.legado.app.ngx.release` 包名不同，可共存安装，互不覆盖。

## 换签名的后果与操作约定

- Android 按签名校验升级：签名变更后**不能覆盖安装**，必须先卸载再装，应用数据会丢。
- 因此从 `3.26.102600` 起，**务必长期备份 `app/legado.jks` 与 `app/signing.properties`**
  （网盘 / 另一台设备各留一份）。再丢一次就得再卸载重装一回。
- 若将来需要再次换签名，在本文件上方新增一节，保留旧记录便于核对历史 APK。

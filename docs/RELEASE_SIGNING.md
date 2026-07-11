# 灵栖 / 灵恋共享发布签名

灵栖与灵恋的正式包必须使用同一发布证书，才能满足
`com.lingqi.app.permission.READ_WELLNESS_SUMMARY` 的 `signature` 权限。

两套 Android 工程默认从仓库外读取：

`%USERPROFILE%\.android\ling-family-signing\signing.properties`

该文件只包含本机签名配置，必须保持 ACL 限制，绝不能复制到项目、日志、
聊天、截图或 Git。配置需要 `storeFile`、`storePassword`、`keyAlias`、
`keyPassword` 四项；缺少任一项时，Release 打包会明确失败，不会退回 debug
签名或生成未签名交付包。

发布前需要分别验证两个 APK 签名有效，并在不输出证书摘要的前提下比较签名
集合完全一致。正式发布后不可随意重建或更换这把密钥；应将整个仓库外签名
目录放入受控的离线加密备份。

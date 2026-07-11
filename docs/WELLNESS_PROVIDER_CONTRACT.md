# 灵栖健康摘要只读接口

此接口只供同一台设备上的灵恋读取用户明确授权的已结束睡眠和冥想摘要。它不提供写入、文件、任意 SQL 查询或原始传感器数据访问。

## 身份与授权

- Provider authority：`com.lingqi.app.wellness`
- 权限：`com.lingqi.app.permission.READ_WELLNESS_SUMMARY`
- 权限级别：`signature`
- 允许的调用包：`app.linglian.mobile`
- 灵栖“我的 → 允许灵恋读取睡眠/冥想摘要”默认关闭；关闭后所有读取返回 `SecurityException`。

生产环境中，灵栖与灵恋必须由同一发布证书签名。开发联调时可让两个 debug APK 使用同一 debug keystore，不得降低权限级别或跳过包名、UID、签名检查。

## 固定查询

- `content://com.lingqi.app.wellness/sleep/recent?limit=10`
- `content://com.lingqi.app.wellness/meditation/recent?limit=10`

`limit` 可省略，默认 10，只接受 1 到 30。调用方不得传 `projection`、`selection`、`selectionArgs` 或 `sortOrder`，也不得传除 `limit` 外的查询参数。

睡眠只返回：`sessionId`、`startedAt`、`endedAt`、`durationSeconds`、`score`、`coverage`、`calibrationNight`。

冥想只返回：`sessionId`、`practiceId`、`startedAt`、`endedAt`、`plannedSeconds`、`actualSeconds`、`completionRate`。

## 隐私边界

接口不暴露进行中的睡眠记录、睡眠 epochs、movement、noise、snore、placement、算法版本、应用偏好、服务检查点或任何原始音频。用户撤销授权时，灵恋应删除本地缓存。

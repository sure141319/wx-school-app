# 生产健康检查与外部故障告警

本方案面向当前单机生产环境，只增加一个只读健康入口和一个校外监控任务，不引入独立监控服务、指标数据库或日志平台。

## 1. 健康端点契约

后端公开：

```text
GET /actuator/health
```

健康时返回 HTTP `200`：

```json
{"status":"UP"}
```

MySQL、Redis、MinIO、磁盘空间或应用自身不可用时，综合状态变为 `DOWN`，返回 HTTP `503`。磁盘默认在可用空间低于 `1GB` 时判定为 `DOWN`，可通过 `HEALTH_DISKSPACE_THRESHOLD` 调整。响应只包含综合状态，不公开组件名称、连接地址、异常或配置。除 `health` 外的 Actuator 端点没有对 Web 暴露。

SMTP 邮件不是商品浏览、搜索和联系方式展示的必要依赖，短时不可用不会把整站判定为宕机；邮件发送失败仍由应用错误日志排查。

## 2. Nginx 只转发精确路径

在生产域名对应的 `server` 块中加入下面两个 `location`。精确规则允许外部监控访问健康端点，后面的规则拒绝其他 Actuator 路径：

```nginx
location = /actuator/health {
    # Nginx 在 Docker 中，复用现有 API 代理访问宿主机的地址。
    proxy_pass http://172.17.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 2s;
    proxy_read_timeout 8s;
    access_log off;
}

location ^~ /actuator/ {
    return 404;
}
```

当前生产配置挂载关系为 `/home/ubuntu/ahut.conf -> /etc/nginx/conf.d/ahut.conf`。修改宿主机文件后，在 Nginx 容器内检查并平滑加载配置：

```bash
sudo docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}'
sudo docker exec <Nginx容器名> nginx -t
sudo docker exec <Nginx容器名> nginx -s reload
```

不要在轻量服务器防火墙或腾讯云防火墙中开放 `8080`；外部探测只通过现有 HTTPS `443` 进入 Nginx。

## 3. 部署后验证

先在服务器本机验证应用：

```bash
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health
```

再从服务器外部验证完整公网链路：

```bash
curl --fail --silent --show-error https://www.ahut-campus.site/actuator/health
```

两次都应只得到 `{"status":"UP"}`。再确认未开放其他端点：

```bash
curl --output /dev/null --silent --write-out '%{http_code}\n' \
  https://www.ahut-campus.site/actuator/env
```

预期为 `404`。

## 4. 配置外部故障告警

推荐先使用 UptimeRobot 的免费 Keyword Monitor；它运行在本服务器之外，服务器、Nginx 或应用整体宕机时仍能发出通知。

创建监控时填写：

| 配置项 | 值 |
|---|---|
| Monitor Type | `Keyword` |
| Friendly Name | `Campus Trade Production Health` |
| URL | `https://www.ahut-campus.site/actuator/health` |
| Keyword | `"status":"UP"` |
| Alert When | `Keyword Not Exists` |
| Monitoring Interval | 免费方案允许的最短间隔 |
| Alert Contacts | 至少绑定一个日常会查看的邮箱，并启用 Down 与恢复通知 |

HTTP `503`、连接失败、超时、TLS/DNS 故障或响应中缺少 `"status":"UP"` 都应触发故障确认流程。创建后必须使用平台的 `Test Notification` 分别测试故障和恢复通知；只创建联系人但没有把联系人绑定到该监控，不会收到告警。

相关平台操作可对照 UptimeRobot 官方的[通知渠道说明](https://help.uptimerobot.com/en/articles/11360978-understanding-notification-channels-in-uptimerobot)与[通知测试说明](https://help.uptimerobot.com/en/articles/11602913-how-to-test-notifications-in-uptimerobot-quick-guide)。

## 5. 上线验收与演练

1. 正常状态下，从手机流量访问健康地址并确认 HTTP `200` 与 `UP`。
2. 在业务低峰期暂时停止后端服务，确认外部监控进入故障状态并收到 Down 通知。
3. 立即恢复后端，确认健康地址恢复，并收到恢复通知。
4. 在运维记录中写下告警到达时间、恢复时间和实际接收人。

演练只需停止 Spring Boot 进程，不要停止 MySQL、Redis 或 MinIO，也不要通过修改生产数据制造故障。

## 6. 收到告警后的最小排查顺序

1. 从校外网络重新访问健康地址，排除单个监控节点误报。
2. 登录服务器检查 Spring Boot 服务状态和最近日志。
3. 检查磁盘空间以及 MySQL、Redis、MinIO 容器状态。
4. 检查 Nginx、证书和域名解析。
5. 恢复后确认收到恢复通知，并记录故障原因和处置过程。

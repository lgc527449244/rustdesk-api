# RustDesk API Server

这是一个基于 Spring Boot 的 RustDesk HTTP 数据 API，用于接收 RustDesk 客户端上报的设备信息、心跳和审计事件，并保存到 MySQL。

本项目不实现 RustDesk 的 ID 注册、连接协商或流量中继，也不能替代 `hbbs` 和 `hbbr`。部署时仍需单独运行 RustDesk Server；本服务只承担客户端配置项 `API Server` 对应的 HTTP 接口。

## 已实现接口

| 方法 | 路径 | 成功响应 | 用途 |
| --- | --- | --- | --- |
| `GET` | `/api/login-options` | `200 application/json`，body 为 `[]` | 客户端登录能力探测；当前未提供账号登录 |
| `POST` | `/api/heartbeat` | 已有完整系统信息时为 `200 {}`；需要上传系统信息时为 `200 {"sysinfo":true}` | 更新设备最后在线时间、协议版本和当前连接 ID |
| `POST` | `/api/sysinfo` | `200 text/plain`，body 严格为 `SYSINFO_UPDATED` | 新增或更新设备系统信息 |
| `POST` | `/api/sysinfo_ver` | `200 text/plain`，body 为 `RUSTDESK_SYSINFO_VERSION` 的值 | 返回稳定的系统信息版本令牌；修改令牌可要求客户端重新上传 |
| `POST` | `/api/audit/conn` | `200`，空 body | 保存连接审计事件 |
| `POST` | `/api/audit/file` | `200`，空 body | 保存文件传输审计事件 |
| `POST` | `/api/audit/alarm` | `200`，空 body | 保存安全告警审计事件 |

审计接口的成功 body 必须为空。RustDesk 客户端会重试带有非空成功响应的审计请求。

`POST /api/heartbeat`、`POST /api/sysinfo` 和 `POST /api/audit/*` 的请求体默认最多为 65536 字节，超过限制返回空 body 的 `413 Payload Too Large`。该限制同样检查 chunked 或其他缺少 `Content-Length` 的请求；`POST /api/sysinfo_ver` 不受此限制。

## 数据库

Flyway 在应用启动时创建两张表，Hibernate 只校验表结构，不自动修改生产表。

- `rustdesk_devices`：每个 RustDesk ID 保存一条最新设备状态，包括 UUID、主机名、用户名、系统、CPU、内存、客户端版本、协议版本、当前连接和最后在线时间。心跳只更新这条记录，不追加心跳历史。
- `rustdesk_audit_events`：追加保存 `conn`、`file`、`alarm` 三类事件，同时提取常用检索字段并保留脱敏后的原始 JSON。文件路径超过 2048 字符时只截断检索列，原始 JSON 中仍保留完整路径。

客户端提供 `nonce` 时，审计事件通过 `(kind, nonce)` 唯一约束实现幂等。重复上报不会重复写入，并仍返回空的 `200`。兼容旧客户端时，缺少 `nonce` 的事件会由服务端生成 UUID，因此此类旧请求无法跨请求去重。

保存原始 JSON 前会递归脱敏。字段名去除 `-`、`_` 并忽略大小写后，如果以 `password`、`secret`、`token`、`authorization`、`cookie`、`privateKey`、`apiKey`、`credential`、`pwd` 或 `passphrase` 结尾，其值会替换为 `[REDACTED]`；RustDesk `info` 这类 JSON 编码字符串也会递归处理。

## 环境要求

- Java 17
- Maven 3.6.3 或更高版本
- Docker 和 Docker Compose，用于启动开发 MySQL
- MySQL 8.0 或更高版本；Compose 推荐并默认使用 `mysql:8.4`

项目使用 Spring Boot 3.5.16，编译目标为 Java 17。

## 快速启动

Compose 文件只启动 MySQL：

```bash
docker compose up -d
docker compose ps
```

显式使用 Java 17 启动应用。macOS 可执行：

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
mvn spring-boot:run
```

其他系统可以将 `JAVA_HOME` 指向实际 JDK 17 目录：

```bash
JAVA_HOME=/path/to/jdk-17 mvn spring-boot:run
```

默认服务地址为 `http://localhost:21114`。健康检查：

```bash
curl http://localhost:21114/actuator/health
curl http://localhost:21114/actuator/health/liveness
curl http://localhost:21114/actuator/health/readiness
```

停止开发数据库：

```bash
docker compose down
```

该命令保留 `rustdesk-mysql-data` 数据卷。

## 环境变量

应用显式读取以下环境变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `MYSQL_HOST` | `localhost` | MySQL 主机 |
| `MYSQL_PORT` | `3306` | MySQL 端口 |
| `MYSQL_DATABASE` | `rustdesk` | 数据库名 |
| `MYSQL_USER` | `rustdesk` | 数据库用户 |
| `MYSQL_PASSWORD` | `rustdesk` | 数据库密码 |
| `SERVER_PORT` | `21114` | HTTP 服务端口 |
| `RUSTDESK_SYSINFO_VERSION` | `1` | 全局系统信息版本令牌；必须保持稳定，需要强制重传时再修改 |
| `RUSTDESK_MAX_REQUEST_SIZE` | `65536` | heartbeat、sysinfo 和 audit POST 请求体的最大字节数 |

`compose.yaml` 还向 MySQL 容器传入 `TZ=UTC`，并支持用 `MYSQL_ROOT_PASSWORD` 和 `MYSQL_ROOT_HOST` 覆盖 root 账号配置。未覆盖时 root 密码默认为 `root`。Compose 中的默认密码只适用于本地开发，生产环境必须替换。

## 配置 RustDesk 客户端

在 RustDesk 客户端的网络设置中，将 `API Server` 配置为本服务的根地址，不要附加 `/api`：

```text
http(s)://host:21114
```

例如，本地或受信任内网可使用 `http://host:21114`；启用 TLS 后可使用 `https://host:21114`。

生产环境应通过反向代理提供 TLS，例如：

```text
https://rustdesk-api.example.com
```

`ID Server` 和 `Relay Server` 仍应分别指向实际的 `hbbs` 和 `hbbr`。

## 请求示例

查询登录能力：

```bash
curl http://localhost:21114/api/login-options
# []
```

首次心跳会创建占位设备，并要求客户端上传系统信息：

```bash
curl -X POST http://localhost:21114/api/heartbeat \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "123456789",
    "uuid": "base64-device-uuid",
    "ver": 1409000,
    "conns": [41],
    "modified_at": 0
  }'
# {"sysinfo":true}
```

上传系统信息：

```bash
curl -X POST http://localhost:21114/api/sysinfo \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "123456789",
    "uuid": "base64-device-uuid",
    "hostname": "workstation-01",
    "username": "alice",
    "os": "Windows / 11 Pro",
    "cpu": "Intel Core, 8/4 cores",
    "memory": "16GB",
    "version": "1.4.9"
  }'
# SYSINFO_UPDATED
```

查询系统信息版本令牌：

```bash
curl -X POST http://localhost:21114/api/sysinfo_ver
# 1
```

连接审计：

```bash
curl -i -X POST http://localhost:21114/api/audit/conn \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "123456789",
    "uuid": "base64-device-uuid",
    "conn_id": 41,
    "session_id": 9001,
    "nonce": "3ae0fd93-650f-4bc9-92f7-6038ad95d222",
    "peer": ["987654321", "Alice"],
    "type": 0,
    "action": "new"
  }'
```

文件审计。RustDesk 的 `info` 字段本身是一个 JSON 字符串：

```bash
curl -i -X POST http://localhost:21114/api/audit/file \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "123456789",
    "uuid": "base64-device-uuid",
    "peer_id": "987654321",
    "conn_id": 42,
    "nonce": "3f0ead29-bad4-463c-a583-77e01a4abc9d",
    "type": 1,
    "path": "/tmp/report.pdf",
    "is_file": true,
    "info": "{\"num\":1,\"files\":[[\"report.pdf\",1024]]}"
  }'
```

告警审计：

```bash
curl -i -X POST http://localhost:21114/api/audit/alarm \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "123456789",
    "uuid": "base64-device-uuid",
    "conn_id": 43,
    "nonce": "0f466fc8-7850-40a3-a162-c2a6ef7ab4b8",
    "typ": 6,
    "info": "{\"ip\":\"192.0.2.10\"}"
  }'
```

## 未实现范围

当前未实现：

- `/api/login`、`/api/currentUser`、`/api/logout` 等账号接口
- `/api/ab/**` 地址簿接口
- 用户、组织、设备组、策略下发、录像上传等 RustDesk Pro 管理 API
- 管理后台和设备、审计数据的读取/查询 API
- `hbbs`/`hbbr` 协议与服务

## 安全边界

原版 RustDesk 客户端向 heartbeat、sysinfo 和被控端审计接口上报时不携带 Bearer token。当前项目也没有引入认证层，因此所有已实现接口及健康检查在网络层面都是可访问的；`id` 和 `uuid` 不能视为可靠身份凭证。

生产部署至少需要：

- 使用 HTTPS；建议由反向代理终止 TLS。
- 在反向代理或防火墙限制来源网络/IP，并配置请求速率限制和请求体大小限制。
- 不将 `21114` 直接暴露到不可信网络。
- 使用独立、最小权限的 MySQL 用户和强密码，并按部署环境启用数据库 TLS。
- 根据 IP、用户名、文件路径等敏感信息制定数据访问和保留策略。

如果必须使用 API Key 或 Bearer token，需要同步修改 RustDesk 客户端，或由可信反向代理为经过认证的请求注入凭证。

## 测试

测试使用独立的内存数据库配置，不依赖本地 MySQL：

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
mvn test
```

也可以显式指定 JDK 路径：

```bash
JAVA_HOME=/path/to/jdk-17 mvn test
```

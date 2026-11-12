# 番茄专注 · MVP v1 开发文档（唯一实现依据）

| 项 | 内容 |
|---|---|
| 版本 | MVP v1.0 |
| 目标 | 5 天内做出每天可用的最小闭环 |
| 平台 | Web（桌面 + 手机浏览器） |
| 技术栈 | Vue3 + Vite + Pinia + Axios + dayjs；Spring Boot 2.7 + MyBatis + MySQL 8；Docker Compose |
| 文档地位 | **第一版只实现本文**。`PRD-番茄专注-v1.md`、`技术方案.md`、`UI设计规范.md` 作为 v2 需求池，暂不实现 |

---

## 1. 一句话定义

打开就能计时，结束后自动存下来，能看到今天和本周学了多久、每次学了什么。

**唯一的闭环：**

```
选模式 → 写任务/事项 → 开始 → 暂停/继续 → 结束 → 自动保存 → 记录页看到
```

---

## 2. 范围

### 2.1 做（P0）

| 模块 | 内容 |
|---|---|
| 计时 | 正计时、倒计时（15/25/45 分钟预设）、开始、暂停、继续、结束 |
| 计时 | 关联任务（自由文本 + 历史下拉）、专注事项（自由文本） |
| 计时 | 倒计时归零自动结束、提示音、结算弹窗 |
| 计时 | 提前结束二次确认，记为「中断」 |
| 计时 | 刷新/关闭后恢复未结束的计时 |
| 记录 | 最近记录列表、删除（软删除） |
| 统计 | 今日总时长、本周总时长、今日番茄数、近 7 天柱状图 |
| 数据 | 保存到 MySQL，重复提交不产生重复记录 |
| 部署 | `docker compose up -d --build` 一键启动 |
| 体验 | 桌面/手机自适应、跟随系统深色模式 |

### 2.2 不做（明确砍掉）

- 任务表、任务 CRUD、完成状态、截止日；
- 日历、月视图、时间块；
- 设置页（时长写死为 15/25/45 三个预设）；
- 统计图表库（用 CSS 柱状图）；
- 导出/导入、PWA、多标签互斥、快捷键、通知、账号；
- 中断筛选、分页、年度统计、连续天数。

> 这些都在 v2 清单里，见第 12 节。

---

## 3. 页面

只有两个路由：

```
/            专注
/records     记录（含统计概览）
```

### 3.1 专注页

```
┌────────────────────────────────────┐
│  番茄专注                  [记录]  │
│                                    │
│         ○  25:00                   │
│           准备开始                  │
│                                    │
│      [15] [25] [45]   （倒计时预设）│
│      [ 正计时 | 倒计时 ]            │
│                                    │
│  任务  [ 数学-高数第三章      ▾ ]  │
│  事项  [ 极限章节习题 1-20      ]  │
│                                    │
│         [    开始专注    ]         │
│                                    │
│  今日 1h20m · 3 个番茄              │
└────────────────────────────────────┘
```

**空闲态：** 可切模式、选预设、填任务与事项；「开始」为主按钮。

**运行态：**

```
         ○  18:42
           专注中
     数学-高数第三章
     极限章节习题 1-20

     [ 暂停 ]   [ 结束 ]
```

- 数字每秒更新，等宽字体；
- 暂停后数字变灰，显示「已暂停」，按钮变「继续」；
- 结束（倒计时未归零）弹确认：「还未完成，结束会记为中断，时长仍会保存。」

**结束态（弹窗）：**

```
本次专注 25:00
数学-高数第三章
[ 再来一次 ]  [ 查看记录 ]
```

### 3.2 记录页

```
┌────────────────────────────────────┐
│  ← 记录                            │
│                                    │
│  今日 1h20m   本周 6h40m   番茄 3  │
│                                    │
│  近 7 天                            │
│  ▁ ▃ ▅ ▂ ▇ ▄ ▆                     │
│  一 二 三 四 五 六 日               │
│                                    │
│  今天                               │
│  14:05–14:30  数学-高数   25m  完成 │
│  15:00–15:12  英语-阅读   12m  中断 │
│                                    │
│  昨天                               │
│  ...                               │
└────────────────────────────────────┘
```

- 顶部三个数字：今日总时长、本周总时长、今日番茄数；
- 近 7 天柱状图用 div 高度百分比实现，不引入图表库；
- 列表按开始时间倒序，最多 100 条；
- 每条右侧「删除」，点击确认后软删除；
- 空态：「还没有记录，去专注一次吧」+ 按钮回专注页。

### 3.3 布局

- 最大宽度 720px，居中，页面上下留白 32px；
- 移动端全宽，左右 16px；
- 顶部一个简单 Header：左标题，右「记录 / 专注」切换；
- 深色模式用 `prefers-color-scheme` 自动切换，不做手动开关。

---

## 4. 计时规则

| 编号 | 规则 |
|---|---|
| R1 | 正计时从 0 向上累计，手动结束即为「完成」 |
| R2 | 倒计时从预设时长递减，归零即「完成」，时长恒等于预设秒数 |
| R3 | 倒计时提前结束需确认，状态为「中断」，时长按实际经过时间（扣除暂停） |
| R4 | 时长 = 结束 − 开始 − 累计暂停，向下取整到秒；小于 1 秒不保存 |
| R5 | 暂停期间不计时、不计入时长；继续后接着算 |
| R6 | 倒计时完成时，`endedAt = startedAt + plannedSec + pausedSec`，避免 tick 误差 |
| R7 | 每次开始生成 `clientId`（UUID），重复提交由后端幂等 |
| R8 | 计时状态存 `localStorage`，键 `focus_active_timer_v1`；只存开始时间戳、暂停累计、任务、事项、模式、预设 |
| R9 | 刷新恢复：倒计时已到期 → 自动按完成保存；正计时未结束 → 提示「继续 / 结束保存 / 丢弃」 |
| R10 | 页面标题在运行中显示 `25:00 · 专注中` |

**计时实现要求：**

```ts
// 只用时间戳，不用 setInterval 累加
elapsedMs = (pausedAt ?? Date.now()) - startedAt - pausedTotalMs
```

`setInterval(250)` 只负责触发重绘。

**结束保存流程：**

1. 组装 payload（含 clientId）；
2. `POST /api/sessions`；
3. 成功：清 localStorage，弹结算；
4. 失败：弹「保存失败，重试 / 放弃」，保留数据在内存与 localStorage；
5. 重复点击结束按钮要防抖，避免重复请求（后端幂等兜底）。

---

## 5. 数据模型

只有一张表：

```sql
CREATE DATABASE IF NOT EXISTS focus_app
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE focus_app;

CREATE TABLE IF NOT EXISTS focus_session (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  client_id    CHAR(36)        NOT NULL COMMENT '客户端 UUID，幂等键',
  task_title   VARCHAR(100)    NOT NULL DEFAULT '' COMMENT '任务名，自由文本',
  note         VARCHAR(200)    NOT NULL DEFAULT '' COMMENT '专注事项',
  mode         VARCHAR(16)     NOT NULL COMMENT 'countup / countdown',
  planned_sec  INT             NULL COMMENT '倒计时预设秒数',
  started_at   DATETIME        NOT NULL,
  ended_at     DATETIME        NOT NULL,
  duration_sec INT             NOT NULL,
  paused_sec   INT             NOT NULL DEFAULT 0,
  status       VARCHAR(16)     NOT NULL COMMENT 'completed / aborted',
  session_date DATE            NOT NULL COMMENT '归属日，服务端按开始时间计算',
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at   DATETIME        NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_client (client_id),
  KEY idx_session_date (session_date, deleted_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

说明：

- 不建任务表；`task_title` 直接从历史记录里取候选值；
- 软删除只用于记录删除；
- 单时区 `Asia/Shanghai`，时间存本地时间；
- 时间格式统一 `yyyy-MM-dd HH:mm:ss`。

---

## 6. 接口（7 个）

统一前缀 `/api`，统一响应：

```json
{ "code": 0, "message": "ok", "data": ... }
```

错误码：`0` 成功、`40000` 参数错误、`40400` 不存在、`50000` 系统异常。

### 6.1 健康检查

`GET /api/health` → `{ "status": "UP" }`

### 6.2 创建会话（幂等）

`POST /api/sessions`

```json
{
  "clientId": "6f1c2a9e-3b7d-4c2a-9f10-2d8e5b7a1c34",
  "taskTitle": "数学-高数第三章",
  "note": "极限章节习题 1-20",
  "mode": "countdown",
  "plannedSec": 1500,
  "startedAt": "2026-11-11 09:00:00",
  "endedAt": "2026-11-11 09:28:12",
  "durationSec": 1500,
  "pausedSec": 192,
  "status": "completed"
}
```

校验：

- `clientId` UUID；`mode` 枚举；`status` 枚举；
- `countdown` 必须有 `plannedSec`，1–10800；
- `durationSec` 1–43200；
- `startedAt < endedAt`；
- `|durationSec − ((endedAt − startedAt) − pausedSec)| ≤ 5`。

响应：`SessionVO`（含 `id`、`sessionDate`）。

幂等：先按 `clientId` 查，存在直接返回；并发重复插入捕获 `DuplicateKeyException` 后再查一次返回。

### 6.3 记录列表

`GET /api/sessions?from=2026-11-05&to=2026-11-11&limit=100`

返回按 `started_at` 倒序的数组，最多 100 条。`from/to` 可省略，默认最近 30 天。

### 6.4 任务名候选

`GET /api/sessions/task-titles?limit=10`

```json
["数学-高数第三章", "英语-阅读", "政治-马原"]
```

SQL：`SELECT task_title FROM focus_session WHERE deleted_at IS NULL AND task_title <> '' GROUP BY task_title ORDER BY MAX(started_at) DESC LIMIT ?`

### 6.5 删除记录

`DELETE /api/sessions/{id}` → 软删除，返回 `null`。

### 6.6 汇总

`GET /api/stats/summary?from=2026-11-11&to=2026-11-11`

```json
{
  "totalSec": 4800,
  "pomodoroCount": 3,
  "abortedCount": 1,
  "activeDays": 1
}
```

### 6.7 每日趋势

`GET /api/stats/daily?from=2026-11-05&to=2026-11-11`

```json
[
  { "date": "2026-11-05", "totalSec": 3600, "pomodoroCount": 2 }
]
```

缺失日期由后端补零，保证返回 7 条。

---

## 7. 后端结构

```
server/src/main/java/com/focus/
├─ FocusApplication.java
├─ common/
│  ├─ CommonResult.java
│  ├─ BizException.java
│  └─ GlobalExceptionHandler.java
├─ controller/
│  ├─ HealthController.java
│  ├─ SessionController.java
│  └─ StatsController.java
├─ service/
│  ├─ SessionService.java
│  └─ impl/SessionServiceImpl.java
├─ mapper/
│  └─ SessionMapper.java
├─ entity/
│  └─ FocusSession.java
├─ dto/
│  └─ SessionCreateReq.java
└─ vo/
   ├─ SessionVO.java
   ├─ SummaryVO.java
   └─ DailyVO.java
```

`resources/mapper/SessionMapper.xml`：

- `insert`；
- `selectByClientId`；
- `selectList`（`<where>` + `from/to/limit`）；
- `selectTaskTitles`；
- `softDelete`；
- `summary`；
- `daily`。

`application.yml` 关键配置：

```yaml
server:
  port: 8080
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/focus_app?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: root
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: Asia/Shanghai
mybatis:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
```

> 不需要 CORS（生产同源，开发用 Vite 代理）。

---

## 8. 前端结构

```
web/src/
├─ main.ts
├─ App.vue
├─ router/index.ts
├─ api/
│  ├─ http.ts
│  └─ session.ts
├─ stores/
│  └─ timer.ts
├─ views/
│  ├─ FocusView.vue
│  └─ RecordsView.vue
├─ components/
│  ├─ TimerRing.vue
│  ├─ SessionItem.vue
│  └─ ConfirmDialog.vue
├─ utils/
│  ├─ date.ts
│  └─ sound.ts
└─ styles/
   └─ main.css
```

依赖只要五个：

```json
{
  "dependencies": {
    "vue": "^3.4.0",
    "vue-router": "^4.3.0",
    "pinia": "^2.1.0",
    "axios": "^1.7.0",
    "dayjs": "^1.11.0"
  }
}
```

**Pinia `timer` store：**

```ts
state: {
  phase: 'idle' | 'running' | 'paused',
  mode: 'countup' | 'countdown',
  presetSec: 1500,
  startedAt: 0,
  pausedAt: 0 | null,
  pausedTotalMs: 0,
  taskTitle: '',
  note: '',
  clientId: '',
}
actions: start, pause, resume, finish, restore, reset
```

**刷新恢复：** `App.vue` 挂载时读 localStorage，按 R9 处理。

**提示音：** Web Audio 合成两声短音，约 15 行代码。

**样式：**

```css
:root {
  --bg: #F5F6F8;
  --surface: #FFFFFF;
  --text: #1F2329;
  --text-2: #6B7280;
  --border: #E6E8EB;
  --primary: #3B6EF6;
  --danger: #E5484D;
  --success: #22A06B;
  --radius: 12px;
}
@media (prefers-color-scheme: dark) {
  :root {
    --bg: #0F1115;
    --surface: #171A21;
    --text: #E6E8EB;
    --text-2: #A1A7B3;
    --border: #2A2F3A;
  }
}
```

计时数字：`font-variant-numeric: tabular-nums; font-size: 64px; font-weight: 600;`

---

## 9. 一键启动

### 9.1 目录

```
focus-app/
├─ docker-compose.yml
├─ .env.example
├─ start.sh
├─ deploy/
│  ├─ mysql/init/01_schema.sql
│  └─ nginx/nginx.conf
├─ server/ (Dockerfile, pom.xml, src)
└─ web/ (Dockerfile, package.json, src, vite.config.ts)
```

### 9.2 compose

```yaml
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: focus_app
      TZ: Asia/Shanghai
    volumes:
      - mysql-data:/var/lib/mysql
      - ./deploy/mysql/init:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-p${MYSQL_ROOT_PASSWORD}"]
      interval: 5s
      timeout: 5s
      retries: 20

  server:
    build: ./server
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/focus_app?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    depends_on:
      mysql: { condition: service_healthy }

  web:
    build: ./web
    ports: ["8080:80"]
    depends_on: [server]

volumes:
  mysql-data:
```

### 9.3 nginx

```nginx
server {
  listen 80;
  root /usr/share/nginx/html;
  location /api/ { proxy_pass http://server:8080/api/; }
  location / { try_files $uri $uri/ /index.html; }
}
```

### 9.4 本地开发

```bash
docker compose up -d mysql
cd server && mvn spring-boot:run          # 8080
cd web && npm i && npm run dev            # 5173，代理 /api → 8080
```

> 本地开发后端用 8080 时，注意别同时开 compose 的 web。或者本地后端改 8081，Vite 代理指向 8081。

---

## 10. 五天计划

| 天 | 任务 | 产出 |
|---|---|---|
| D1 | 建库建表；Spring Boot 骨架；`POST/GET sessions`；health | Postman 能增能查 |
| D2 | 前端骨架 + 专注页 UI + 计时状态机（本地跑通） | 能开始/暂停/结束，控制台打印结果 |
| D3 | 结束保存、记录页列表、删除、刷新恢复 | 数据落库，记录可见 |
| D4 | 汇总/趋势接口 + 记录页顶部数字与 7 天柱状 | 统计正确 |
| D5 | Docker 三件套、README、空态/深色/移动端、验收 | 一条命令启动 |

### 给 Trae 的投喂顺序

1. 「按第 5 节建表，生成 `01_schema.sql`；按第 7 节生成 Spring Boot 骨架和 `POST /api/sessions`，包含校验与幂等。」
2. 「生成 `GET /api/sessions`、`task-titles`、`DELETE`，MyBatis XML 用 `<where>`/`<if>`。」
3. 「生成 `summary` 和 `daily` 两个统计接口，daily 缺失日期补零。」
4. 「用 Vue3 + Pinia 实现第 3 节的专注页与计时器，严格按第 4 节规则，先不接后端。」
5. 「接后端保存，实现记录页与 7 天柱状图。」
6. 「补 Dockerfile、compose、nginx，保证 `docker compose up -d --build` 可用。」

每次只做一步，跑通再下一步。

---

## 11. 验收清单

- [ ] `docker compose up -d --build` 后 `http://localhost:8080` 可打开。
- [ ] 正计时 30 秒结束，记录页出现「30 秒 / 完成」。
- [ ] 倒计时 25 分钟归零，自动保存，时长正好 1500 秒。
- [ ] 倒计时进行 10 分钟提前结束，确认后记为「中断」，时长 10 分钟。
- [ ] 暂停 2 分钟后继续并结束，暂停时长 2 分钟，专注时长不含它。
- [ ] 刷新页面后，运行中的计时恢复且时间正确。
- [ ] 倒计时结束前关闭页面，10 分钟后打开，自动补记一条。
- [ ] 同一 `clientId` 重复提交，数据库只有一条。
- [ ] 记录页今日/本周总时长与数据库一致。
- [ ] 删除记录后列表与统计同步减少。
- [ ] 手机 375px 宽度可用，无横向滚动。
- [ ] 系统深色模式下页面跟随。

---

## 12. v2 清单（做完 MVP 再碰）

| 优先级 | 功能 |
|---|---|
| 高 | 任务表 + 任务页（完成、截止日） |
| 高 | 日历周视图 |
| 高 | 设置页（自定义时长、自动休息、声音） |
| 中 | 中断筛选、分页、连续天数 |
| 中 | 导出 JSON、备份提醒 |
| 中 | PWA、多标签互斥、快捷键 |
| 低 | 月视图、年度统计、图表库、通知、账号 |

到那时再读 `PRD-番茄专注-v1.md` 和 `技术方案.md`。

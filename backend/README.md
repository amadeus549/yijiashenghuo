# 逸家生活 · 品质到家服务平台

> 一个基于 Spring Boot + Redis 的到家服务交易平台：私厨上门、宴席调酒、派对策划、上门推拿、美容美发、私教、育婴等 10 类上门服务，包含商户浏览、服务笔记、优惠套餐、限时抢购、关注点赞、评价互动等完整链路。

本项目的价值不在"业务 CRUD"，而在于**用 Redis 解决了高频场景下的缓存、并发、计数三类典型问题**，并且每一处都有可复现的验证数据。

> 想快速了解项目定位、技术亮点和面试口径，看 **[docs/项目介绍.md](docs/项目介绍.md)**。

## 页面预览

| 首页 | 服务商详情 | 服务笔记 |
|---|---|---|
| ![首页](docs/screenshots/home.png) | ![服务商详情](docs/screenshots/provider-detail.png) | ![服务笔记](docs/screenshots/note-detail.png) |

---

## 一、技术栈

| 层次 | 选型 |
|---|---|
| 后端 | Spring Boot 2.3.12、MyBatis-Plus 3.5.3.2、Lombok |
| 存储 | MySQL 8（业务数据）、Redis 6（缓存 / 计数 / 分布式锁 / 消息队列） |
| 缓存 | Spring Data Redis + Lettuce 连接池、Redisson 3.13（分布式锁） |
| 前端 | Vue 2 + Element UI + Axios（移动端 H5，Nginx 托管） |
| 构建 | Maven（JDK 11） |

---

## 二、核心功能与技术点

### 1. 缓存：一套代码覆盖穿透 / 击穿 / 雪崩

`CacheClient` 里封装了三种策略，按业务特点分别使用：

| 策略 | 适用场景 | 实现要点 |
|---|---|---|
| 空值缓存（穿透） | 服务商详情、服务分类 | 查不到时写入空串，TTL 2 分钟，挡住"用一个不存在的 id 反复打库" |
| 互斥锁重建（击穿） | 热点服务商 | `SETNX` 拿锁 + 双检，只放一个线程回源 |
| 逻辑过期（击穿） | 首页热门服务商 | value 里带 `expireTime`，物理不过期；过期后异步重建，请求永远不阻塞 |

配套的 `CacheWarmUpRunner` 在启动时把销量 Top 200 的服务商预热进 Redis（实测 12 条 75ms），避免冷启动瞬间打穿数据库。

> 一个容易被忽略的坑：逻辑过期方案的前提是"key 一定存在"。缓存缺失时如果不回源，就会把正常数据误判成"不存在"。本项目在 `queryWithLogicalExpire` 里补了缺失回源 + 空值标记两条分支。

### 2. 限时抢购：Lua + 异步下单，扛住瞬时并发

```
请求 → Redis Lua 原子校验（库存 + 一人一单）→ 扣减库存 → 投递阻塞队列 → 立即返回订单号
                                                              ↓
                                            独立消费线程 + Redisson 锁 + 事务落库
```

- **Lua 脚本**（`flash.lua`）把"判断库存、判断重复下单、扣库存、记录下单用户"合成一次原子操作，杜绝超卖；
- **异步削峰**：下单动作进 `ArrayBlockingQueue`，由独立线程池消费，接口响应不再等数据库；
- **Redisson 分布式锁** 保证同一用户在多实例下不会重复建单；
- **事务代理**：消费线程里 `UserHolder` 的 ThreadLocal 已失效，提前在请求线程取好 `proxy`，保证 `@Transactional` 生效。

### 3. 附近的服务商：Redis GEO

商户坐标写入 `GEO`，按 `GEOSEARCH` 由近到远分页返回，并回填 distance。前端默认定位上海人民广场（121.4737, 31.2304）。

### 4. 点赞：Set 去重 + ZSet 排序

- `note:liked:{noteId}` 用 Set 存点赞用户，`SISMEMBER` 判重，天然幂等；
- `note:likes:{userId}` 用 ZSet（score = 时间戳）存"我点赞过的笔记"，按时间倒序分页，解决"点赞列表按时间排序"的需求。

### 5. 关注与 Feed 流

关注关系双写（`t_follow` + Redis Set），支持"是否关注""共同关注"；`/note/of/follow` 用 `ZSet` 做收件箱模式，按时间滚动分页拉取关注人的笔记。

### 6. 签到：BitMap

`sign:{userId}:{yyyyMM}` 一个用户一个月一个 key，`SETBIT` 按"日"写位，`BITFIELD GET u{day}` 一次取出本月至今的位串，再从低位往高位数连续的 1 得到连续签到天数。

> 一个月只占 4 字节，一年 48 字节。跨月靠 key 里的 `yyyyMM` 天然隔离。

### 7. UV 统计：HyperLogLog

`PFADD uv:{yyyy-MM-dd} {userId}` 按天计数，区间统计用 `PFMERGE` 合并后 `PFCOUNT`，跨天自动去重。

实测对比（同一台 Redis）：

| 方案 | 规模 | 结果 | 内存 |
|---|---|---|---|
| HyperLogLog | 10 万 | 99,659（误差 0.34%） | 14.4 KB |
| Set | 10 万 | 100,000（精确） | 5.3 MB |
| HyperLogLog | 100 万 | 991,884（误差 0.81%） | 14.4 KB |

同样 10 万人，Set 内存是 HLL 的 **387 倍**；HLL 从 10 万到 100 万内存几乎不变。

UV 打点挂在登录拦截器里：只要携带有效 token 访问任意接口，当天 UV 自动 +1，统计异常不影响主流程。

### 8. 服务评价：真实数据 + 频控

- 评价支持两级结构（`parent_id` / `answer_id`），回复时展示"回复 XXX"；
- 列表一次性批量查出评论人与被回复人，避免 N+1；
- 发布用 `SETNX + 5s TTL` 做频控，挡住连点；
- 评价数在 `t_note.comments` 上原子自增，详情页不用每次 `count(*)`；
- 服务商详情页聚合展示"该服务商名下所有笔记的评价"，点击可跳转原笔记。

---

## 三、与原"点评"类项目的差异

这不是简单的改包名，业务模型做了完整的重新映射：

| 原模型 | 本项目 | 说明 |
|---|---|---|
| Shop 商铺 | **Provider 服务商** | 从"到店消费"变成"上门服务"，新增服务范围、服务时间 |
| ShopType 商铺分类 | **ServiceType 服务分类** | 私厨到家 / 宴席调酒 / 派对策划 / 轰趴管家 / 上门推拿 / 美容 SPA / 上门美发 / 上门美甲 / 健身私教 / 育婴照护 |
| Blog 探店笔记 | **Note 服务笔记** | 从"探店打卡"变成"服务体验记录" |
| Voucher 优惠券 | **Deal 特惠套餐** | 按次/按人次计价 |
| SeckillVoucher 秒杀券 | **FlashDeal 限时抢购** | 保留 Lua + 异步下单全链路 |
| VoucherOrder 订单 | **DealOrder 服务订单** | 含支付、核销（use_time）、退款时间 |
| BlogComments | **NoteComment 服务评价** | 补齐了原项目只有空 Controller 的评价模块 |

工程层面：包名 `com.hmdp` → `com.yijia`，工程 `yijia-life`，前端目录 `html/yijia`，数据库 `yijia`，表名统一 `t_*` 前缀，接口路径 `/shop|/blog|/voucher` → `/provider|/note|/deal`。

演示数据也全部重写，且由脚本生成、可复现：**10 个服务分类、60 个服务商、120 个套餐、20 个限时抢购、
90 篇服务笔记、900 条评价、40 个用户、160 条关注关系**。服务商分布在 20 个上海真实商圈，
坐标可用于 GEO 附近查询；抢购时间用 `NOW()` 计算，任何时间导入都处于进行中。

---

## 四、快速启动

### 1. 准备环境

- **JDK 11**（推荐）、Maven 3.6+
- MySQL 8（`127.0.0.1:3306`）
- Redis 6（默认 `192.168.66.129:6379`，密码 `423656514`；可用 `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` 环境变量覆盖）

> **JDK 版本**：JDK 11 ~ 24 都能直接跑，不需要任何额外 JVM 参数。
> 早期版本用的是 MyBatis-Plus 3.4.3，它的 Lambda 条件构造器要反射 `SerializedLambda` 的私有字段，
> 在 JDK 16+ 会抛 `InaccessibleObjectException`；现已升级到 **3.5.3.2**（改为调用公开 API，不再反射 JDK 内部字段），
> 从根上消除了这个问题。若你把依赖降回 3.4.x，仍需在 VM options 加 `--add-opens java.base/java.lang.invoke=ALL-UNNAMED`。

### 2. 初始化数据库

```bash
mysql -uroot -p -e "CREATE DATABASE yijia DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -uroot -p --default-character-set=utf8mb4 yijia < src/main/resources/db/yijia-schema.sql
mysql -uroot -p --default-character-set=utf8mb4 yijia < src/main/resources/db/yijia-seed.sql
```

> `yijia-schema.sql` 只有表结构，`yijia-seed.sql` 是演示数据，两者分开，方便清库重来。
>
> 演示数据由 `tools/gen_seed.py` 生成（固定随机种子，改完脚本重跑即可）：

### 3. 启动后端

```bash
mvn spring-boot:run
# 或
mvn clean package -DskipTests && java -jar target/yijia-life-0.0.1-SNAPSHOT.jar
```

默认端口 `8083`。

### 4. 启动前端

把 `html/yijia` 放到 Nginx 的 `html` 目录，`/api` 反向代理到后端：

```nginx
server {
    listen 8088;
    location / { root html/yijia; index index.html; }
    location /api {
        rewrite /api(/.*) $1 break;
        proxy_pass http://127.0.0.1:8083;
    }
}
```

访问 `http://localhost:8088`。

### 5. 登录

验证码走"发送验证码 → 存 Redis（5 分钟）"，本地开发直接取 Redis 里的值：

```bash
curl -X POST "http://localhost:8083/user/code?phone=13800138000"
redis-cli -h <host> -a <pwd> get login:code:13800138000
```

演示账号（`t_user`）：

| 手机号 | 昵称 |
|---|---|
| 13800138000 | 沪上阿伟 |
| 13838411438 | 微醺小满 |
| 13900001111 | 萌宝妈妈 |
| 13456789011 | 肝码自救中 |
| 13456789001 | 指尖有花 |
| 13686869696 | 林间有风 |

---

## 五、接口一览

| 模块 | 接口 |
|---|---|
| 用户 | `POST /user/code`、`POST /user/login`、`GET /user/me`、`GET /user/{id}`、`GET /user/info/{id}` |
| 签到 | `POST /user/sign`、`GET /user/sign/count` |
| 分类 | `GET /service-type/list`（Redis 缓存） |
| 服务商 | `GET /provider/{id}`、`GET /provider/of/type`（GEO 附近）、`GET /provider/of/name`、`POST/PUT /provider` |
| 套餐 | `GET /deal/list/{providerId}`、`POST /deal`、`POST /deal/flash` |
| 抢购 | `POST /deal-order/flash/{dealId}` |
| 订单 | `GET /deal-order/list`、`POST /deal-order/{id}/pay`、`POST /deal-order/{id}/cancel` |
| 笔记 | `GET /note/hot`、`GET /note/{id}`、`POST /note`、`PUT /note/like/{id}`、`GET /note/likes/{id}`、`GET /note/of/me`、`GET /note/of/follow` |
| 评价 | `GET /note-comments/of/note/{noteId}`、`GET /note-comments/of/provider/{providerId}`、`POST /note-comments`、`DELETE /note-comments/{id}` |
| 关注 | `PUT /follow/{id}/{isFollow}`、`GET /follow/or/not/{id}`、`GET /follow/common/{id}` |
| UV | `GET /uv/today`、`GET /uv/{date}`、`GET /uv/range?start=&end=`、`POST /uv/record` |
| 上传 | `POST /upload/note` |

---

## 六、目录结构

```
yijia-life
├── src/main/java/com/yijia
│   ├── config         # MyBatis-Plus、MVC 拦截器、Redisson、缓存预热
│   ├── controller     # 接口层
│   ├── dto            # Result / VO / 表单对象
│   ├── entity         # 实体（Provider / Note / Deal / FlashDeal / DealOrder ...）
│   ├── mapper
│   ├── service        # 业务实现（缓存、GEO、点赞、关注、抢购、评价、UV）
│   └── utils          # CacheClient / RedisConstants / 拦截器 / 分布式锁
├── src/main/resources
│   ├── db             # yijia-schema.sql（结构）、yijia-seed.sql（演示数据）
│   ├── flash.lua      # 抢购原子脚本
│   └── application.yaml
├── tools
│   └── gen_seed.py    # 演示数据生成器（固定随机种子，改完重跑即可）
└── pom.xml
```

---

## 七、已知限制

1. **素材图仍是通用生活方式图**，与"上门服务"的场景并非严格对应（例如推拿笔记用了餐食图）。生产环境应替换为真实服务场景图。
2. **UV 目前只统计登录用户**，匿名访客不计入，更接近"日活"而非全站 UV。
3. **抢购队列是 JVM 内的 `ArrayBlockingQueue`**，单机可用；多实例部署需要换成 Redis Stream / RocketMQ，代码里已把"投递"和"消费"分离，替换成本不高。
4. **验证码没有接短信通道**，本地从 Redis 直接取；生产需接入短信服务并对 `sendCode` 加频控。
5. **评价没有做敏感词与图片上传**，仅做了长度校验和 5 秒频控。

---

## 八、常见启动问题

| 现象 | 原因 | 解决 |
|---|---|---|
| `InaccessibleObjectException: ... does not "opens java.lang.invoke"` | 依赖被降回 MyBatis-Plus 3.4.x（3.5.3.2 没有此问题） | 加 `--add-opens java.base/java.lang.invoke=ALL-UNNAMED`，或改用 JDK 11 |
| `Web server failed to start. Port 8083 was already in use` | 端口被占用（常见于上次没关干净的实例） | 换端口 `--server.port=8084`，或结束占用进程：`netstat -ano \| findstr :8083` 找到 PID 后 `taskkill /F /PID <pid>` |
| `启动失败：连不上 Redis x.x.x.x:6379` | Redis 没起、密码不对、虚拟机没开机或 6379 没放行 | 按提示逐项排查；只想本机跑就设 `REDIS_HOST=127.0.0.1` |
| `Communications link failure` / `Access denied for user 'root'` | MySQL 没起或密码不对 | 改 `application.yaml` 的 `spring.datasource`，先确认 `mysql -uroot -p` 能连上 |
| `Unknown database 'yijia'` | 没执行建库脚本 | 按「2. 初始化数据库」重新导入 |
| 页面能开但数据全空、控制台报 401 | 没登录，token 过期 | 重新登录（验证码从 Redis 的 `login:code:{手机号}` 取） |

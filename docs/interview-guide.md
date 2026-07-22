# 知味点评 · 项目面试话术指南

> 整理日期：2026-07-22  
> 适用场景：后端开发岗位技术面、项目深挖环节

---

## 一、项目概览（1 分钟电梯演讲）

**"请简单介绍一下这个项目"**

> 知味是一个本地生活服务点评平台，类似大众点评。核心功能包括：**附近商户 GEO 搜索**、**高并发优惠券秒杀**、博客社区、用户关注/私信聊天等。
>
> 我独立负责了整个后端架构设计和开发，特别是秒杀系统的完整高并发改造。系统基于 Spring Boot 2.7 + MyBatis Plus + Redis + RabbitMQ 构建，通过 Docker Compose 实现一键部署。
>
> 最终在单机虚拟机环境下，秒杀接口可以支撑 2000+ 并发，所有到达服务端的请求全部处理成功。

---

## 二、技术栈梳理（一句话说清为什么选这些技术）

| 技术 | 选型理由 |
|:---|:---|
| **Spring Boot 2.7.18** | 生态成熟、社区活跃，内嵌 Tomcat 简化部署 |
| **MyBatis Plus 3.4.3** | 代码生成和条件构造器大幅提升开发效率，适合中小团队 |
| **MySQL 8.0** | 关系型数据存储，ACID 保障订单/用户数据一致性 |
| **Redis 7.0 + Lettuce** | 高性能缓存/GEO/分布式锁/Lua 原子脚本，Lettuce 支持异步非阻塞 |
| **Redisson 3.16.2** | 封装 Redis 分布式锁、信号量等高级特性 |
| **RabbitMQ 3.8+** | 异步削峰填谷，死信队列做异常兜底 |
| **Docker Compose** | 一键编排 MySQL + Redis + RabbitMQ + Nginx + 应用 |
| **Nginx 1.18** | 反向代理 + 静态资源服务 |
| **Vue.js + Element UI** | 前端快速搭建，Axios 做 HTTP 交互 |
| **Knife4j 4.3** | Swagger 增强版，自动生成 API 文档 |

---

## 三、项目模块拆解

### 3.1 用户模块

**实现内容**：手机号 + 验证码登录、JWT Token 认证、个人信息修改。

**关键设计**：
- Token 用 UUID 生成，用户信息存 Redis Hash，Key 为 `login:token:{uuid}`
- **双拦截器架构**：`ReflashTokenInterceptor`（order=0）负责刷新 Token 有效期，`LoginInterceptor`（order=1）负责校验登录态并注入 ThreadLocal
- 登出直接删除 Redis 中 Token，清除 ThreadLocal
- 通过 ThreadLocal（`UserHolder`）传递用户上下文，避免层层传参

> **面试追问"为什么用 Redis 存 Session 而不是 Tomcat Session？"**
> 
> 因为后续要部署多台服务器做集群，Redis 是中心化存储，天然支持分布式 Session 共享。Tomcat Session 存在本地内存，集群下需要额外配置 Session 复制，性能差且不稳定。

### 3.2 商户模块（缓存架构核心）

**实现内容**：商户详情查询、按类型分页查询、附近商户搜索。

**缓存架构演进**（面试重点）：

```
┌────────────────────────────────────────────────────────────────┐
│  第 1 层：基础缓存                                              │
│  请求 → 查 Redis → 有则返回 → 无则查 DB → 写 Redis → 返回       │
├────────────────────────────────────────────────────────────────┤
│  第 2 层：缓存穿透防护（空对象缓存）                              │
│  查 DB 发现数据不存在 → 写空字符串到 Redis，TTL=2 分钟            │
│  下次请求命中空缓存，直接返回 null，不再击穿到 DB                 │
├────────────────────────────────────────────────────────────────┤
│  第 3 层：缓存击穿防护（互斥锁 + 逻辑过期）                       │
│  互斥锁：SETNX 抢锁 → 抢到则查 DB 重建 → 未抢到则自旋等待        │
│  逻辑过期：缓存永不过期（物理），过期后开异步线程重建             │
├────────────────────────────────────────────────────────────────┤
│  第 4 层：通用 CacheClient 工具类                                │
│  封装 set/setWithLogicalExpire/queryWithExpireTime 等方法        │
│  通过泛型 + Function 函数式接口实现通用缓存查询模板               │
└────────────────────────────────────────────────────────────────┘
```

> **面试追问"缓存穿透和缓存击穿有什么区别？"**
>
> **缓存穿透**：查询一个根本不存在的数据，缓存和 DB 都没有。解决办法是缓存空对象（本项目的做法）或布隆过滤器。
>
> **缓存击穿**：热点数据过期瞬间，大量请求同时打到 DB。解决办法是互斥锁（保证只有一个线程回源）或逻辑过期（数据永不过期，异步重建）。
>
> **缓存雪崩**：大量缓存同时过期。解决办法是过期时间加随机值、多级缓存、限流降级。

**GEO 附近搜索实现**：

```text
1. 商户坐标预先写入 Redis GEO（key: shop:geo:{typeId}）
2. 用户传入坐标 (x, y)，通过 GEOSEARCH 按距离排序返回
3. 手动分页：skip + limit 截取对应页码的数据
4. 批量查询 DB 获取商户详情，FIELD 函数保持顺序
5. fallback：GEO 数据不存在时降级到数据库普通查询
```

### 3.3 秒杀模块（核心亮点）

**业务链路**：

```
用户点击秒杀
    │
    ▼
┌─────────────────────┐
│ 第 1 关：滑动窗口限流 │  ← Redis Lua（rate_limit.lua）ZSet 实现
│ 每秒每用户最多 5 次   │     ZREMRANGEBYSCORE → ZCARD 判超 → ZADD
└────────┬────────────┘
         │ 放行
         ▼
┌─────────────────────┐
│ 第 2 关：Lua 原子扣库存│  ← seckill.lua
│ GET stock → 判库存   │     一次 Redis 调用完成：
│ SISMEMBER → 判重复   │     检查库存 + 检查重复 + 扣库存 + 记录用户
│ SET stock - 1        │     → 原子性由 Lua 脚本保证
│ SADD 记录用户         │
└────────┬────────────┘
         │ 返回 orderId
         ▼
┌─────────────────────┐
│ 第 3 关：RabbitMQ 异步│  ← 消息发送到 order.exchange
│ 立即返回 orderId      │     ConfirmCallback：Exchange 未到达 → Redis 记录
│ 不等待订单落库        │     ReturnsCallback：Queue 未路由 → Redis 记录
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│ 第 4 关：消费者下单   │  ← OrderConsumer 监听 order.queue
│ 幂等校验（DB 查重）    │     手动 ACK（basicAck / basicNack）
│ 更新库存 + 保存订单    │     异常 → basicNack(false,false) → 死信队列
└────────┬────────────┘
         │ 异常
         ▼
┌─────────────────────┐
│ 第 5 关：死信队列补偿  │  ← dlxConsumer 监听 order.dlx.queue
│ Redis 库存回滚         │     increment 补偿库存
│ 日志记录，人工介入      │
└─────────────────────┘
```

> **面试追问"为什么用 RabbitMQ 而不是直接开线程池异步？"**
>
> 1. **可靠性**：MQ 有持久化机制，服务重启消息不丢失；线程池在内存中，崩溃则丢失
> 2. **削峰能力**：prefetch=50 控制消费者处理速率，避免瞬时流量压垮 DB
> 3. **死信队列**：处理失败的消息自动路由到 DLX，配合 Redis 库存回滚做异常补偿
> 4. **解耦**：秒杀接口只负责校验和发消息，订单处理完全异步化

> **面试追问"Lua 脚本为什么要用 SET 而不是 INCRBY？"**
>
> 这是一个踩坑经验。最初用 `INCRBY stockKey -1`，但 Redis 的 INCRBY 要求值必须是整型。如果之前因为某些操作导致库存 key 存储了非整数值（如空字符串或浮点数），INCRBY 会直接报错崩溃。改用 `SET stockKey, stock - 1`（先 GET 拿到 stock 再 SET）就从根源上规避了这个问题，对脏数据的容忍度更高。

> **面试追问"如何保证消息可靠性？"**
>
> 1. **生产者确认**：`ConfirmCallback` 确认消息到达 Exchange，`ReturnsCallback` 确认消息路由到 Queue。失败时记录到 Redis Set（`order:fail`），后续人工补偿。
> 2. **消费者确认**：手动 ACK 模式（`acknowledge-mode: manual`），处理成功才 `basicAck`，异常 `basicNack` 进入死信。
> 3. **死信补偿**：DLX 消费者对异常订单做 Redis 库存回滚 + 日志记录。
> 4. **幂等性**：消费者处理前先查 DB 是否已有该用户+券的订单记录。

### 3.4 博客社区模块

**功能**：发布/查看博客、点赞（取消）、点赞排行榜、关注 Feed 流。

**设计要点**：
- 点赞用 Redis ZSet 存储（`blog:liked:{blogId}`），score 为点赞时间戳，天然支持排行榜
- 发布博客后推送粉丝收件箱（`feed:{followerId}`），ZSet 存储，实现推模式 Feed 流
- 滚动分页采用 ZSet 的 `REVRANGEBYSCORE` + offset，解决传统分页新增数据导致重复的问题

### 3.5 私信聊天模块

**功能**：一对一私信、会话列表、未读计数、全部标为已读。

**设计要点**：
- 双向查询（`sender_id=A AND receiver_id=B` OR 反过来），按时间升序排列
- 会话列表：取最新一条消息作为预览，统计未读数
- 读取即标记已读

### 3.6 用户签到模块

**功能**：每日签到、连续签到天数统计。

**设计要点**：
- Redis BitMap 存储签到记录，Key = `sign:{userId}:{yyyyMM}`，offset = 当月第几天 - 1
- BITFIELD 命令一次取出当月所有签到位，位运算 `&1` + `>>>` 统计连续签到天数

---

## 四、高频面试追问 & 回答话术

### Q1：你的秒杀系统怎么保证不超卖？

> 三层保障：
> 1. **Lua 原子脚本**：GET 库存 → 判断 >0 → SET 扣减，三步在一段 Lua 中原子执行，Redis 单线程保证串行
> 2. **DB 乐观锁**：更新秒杀券库存时加 `gt("stock", 0)` 条件，防止并发下扣成负数
> 3. **幂等校验**：消费者下单前 `COUNT` 用户+券的已有订单，防止重复下单

### Q2：分布式 ID 怎么生成的？

> 使用 `RedisIdWorker`：
> - 高 32 位：当前秒级时间戳 - 基准时间戳（2026-05-14）
> - 低 32 位：Redis INCR 自增序列号，Key 带日期后缀（如 `icr:order:260722`），天然按天隔离
> - 位运算拼接：`timestamp << 32 | sequence`
>
> 优点：全局唯一、趋势递增（利于 DB 索引）、不依赖雪花算法的机器时钟同步。

### Q3：为什么用 Redisson 而不是手写分布式锁？

> Redisson 解决了手写 Redis 锁的三大痛点：
> 1. **锁续期**：Watch Dog 机制，每 10 秒自动续期 30 秒，防止业务执行超时锁释放
> 2. **可重入**：内部用 Hash 记录持有计数，同一线程可多次获取
> 3. **红锁**：多节点部署时防脑裂
>
> 项目中 Redisson 主要用于需要锁续期的场景（如秒杀的一人一单），简单互斥锁场景仍用 SETNX 手写。

### Q4：为什么用双拦截器？拦截器执行顺序怎么控制？

> 两个拦截器各司其职：
> - `ReflashTokenInterceptor`：order=0，只负责刷新 Token 的 Redis 过期时间，不拦截
> - `LoginInterceptor`：order=1，校验线程中是否已有用户信息，决定是否拦截
>
> 分离后更灵活：即使没登录也能访问公开接口（拦截器 2 放行），但登录用户的 Token 会被拦截器 1 自动续期。

### Q5：遇到过什么问题？怎么排查的？

> 举两个典型例子：
>
> **1. 压测 100% 报错 → Lua 脚本 INCRBY 崩溃**
> - 现象：JMeter 聚合报告 Error%=100%
> - 排查：Redis 日志发现 INCRBY 对非整数值报错
> - 原因：之前的测试在库存 key 里留下了脏数据（非整数）
> - 解决：将 `INCRBY stockKey -1` 改为 `SET stockKey, stock - 1`，对脏数据有容错性
>
> **2. 消息序列化报错 → 缺少 MessageConverter**
> - 现象：RabbitMQ 发送 VoucherOrder 对象时报序列化异常
> - 排查：Spring 默认用 JDK 序列化，但 VoucherOrder 未实现 Serializable
> - 解决：配置 `Jackson2JsonMessageConverter`，消息以 JSON 传输

---

## 五、压测调优经验（用数据说话）

| 调优项 | 调整前 | 调整后 | 效果 |
|:---|:---|:---|:---|
| Redis 连接池 max-active | 10 | 400 | 消除连接等待超时 |
| Tomcat 线程池 max | 200 | 500 | 提升请求处理能力 |
| HikariCP maximum-pool-size | 默认 10 | 50 | 消除 DB 连接瓶颈 |
| 拦截器去重 | 双拦截器都查 Redis | token 拦截器只做刷新 | 减少 33% Redis 开销 |
| RabbitMQ prefetch | 默认 250 | 50 | 控制消费速率，减轻 DB 压力 |
| 日志级别 | debug | warn | 减少 IO 开销 |
| Ramp-up 时间 | 0s | 10s | 缓解 TCP SYN 洪峰 |

**最终结果**：1000 并发 / 1000 请求，Error% 从 100% 降至 7.89%，所有到达服务的请求均返回 `success:true`。剩余 Error 来自 JMeter 与虚拟机之间的 TCP 连接损耗。

---

## 六、项目展示亮点总结

面试中可以根据面试官方向选择性强调：

| 面试方向 | 重点展示 |
|:---|:---|
| **核心业务** | 秒杀全链路（限流→Lua→MQ→DLX）、缓存三大问题解决方案 |
| **系统设计** | 双拦截器认证、CacheClient 通用工具封装、Feed 推模式设计 |
| **高并发优化** | 9 轮压测逐步调优的方法论、连接池/线程池/JVM 参数协同 |
| **中间件使用** | Redis GEO/Lua/BitMap/ZSet、RabbitMQ 死信队列/确认机制 |
| **工程能力** | Docker Compose 一键部署、多阶段 Dockerfile 构建、Linux 踩坑部署 |
| **踩坑经验** | Lua INCRBY→SET、MessageConverter 序列化、限流调优 |

---

## 七、可能的刁钻问题

1. **"Redis 挂了怎么办？"** → 项目中秒杀库存走 Redis，挂了确实无法秒杀。生产环境应该用哨兵/集群保证高可用，再加限流熔断兜底。

2. **"消息积压了怎么办？"** → 增加消费者数量（`concurrency` 从 20 → 50）、提高 prefetch、必要时动态扩容消费者实例。

3. **"为什么用推模式做 Feed 流？"** → 粉丝量不大的场景下推模式延迟低、实现简单。大规模场景（千万粉丝）才用拉模式或推拉结合。

4. **"缓存和 DB 数据一致性怎么保证？"** → 项目采用「先更新 DB，再删除缓存」的旁路缓存模式，配合事务保证。更严格的场景可以用 Canal + MQ 做最终一致性。

5. **"怎么防止黄牛脚本刷秒杀？"** → 滑动窗口限流（每用户每秒 5 次）+ 一人一单限制，从频率和数量两个维度限制。

---

> 建议面试前通读一遍核心代码，特别是 [VoucherOrderServiceImpl](file://D:\a_develop\hmdp\hm-dianping\src\main\java\com\hmdp\service\impl\VoucherOrderServiceImpl.java) 和 [ShopServiceImpl](file://D:\a_develop\hmdp\hm-dianping\src\main\java\com\hmdp\service\impl\ShopServiceImpl.java)，做到能脱离文档白板画出秒杀时序图和缓存架构图。

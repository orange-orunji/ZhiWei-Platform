# 知味·本地生活智能推荐 - 本地生活服务点评平台

> 一个基于 Spring Boot + Redis + MySQL 的实战项目，类似大众点评的基础功能实现。

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## 📖 项目概述

### 代做哦

## 🛠 技术栈

| 类别 | 技术 | 说明 |
|------|------|------|
| 后端框架 | Spring Boot 2.3.12 | 核心业务逻辑 |
| ORM | MyBatis Plus | 数据库操作 |
| 数据库 | MySQL 8.0 | 持久化存储 |
| 缓存 | Redis 7.0+ | GEO 位置搜索、HyperLogLog UV 统计、Lua 库存扣减 |
| 前端 | Vue.js + Element UI + Axios | 单页面应用 |
| Web 服务器 | Nginx 1.18.0 | 前端部署、反向代理 |
| 认证 | JWT + 拦截器 | 登录校验、ThreadLocal 线程隔离 |
| 构建工具 | Maven + docker | 依赖管理与打包、compose一键部署 |

## ✨ 功能亮点

- **附近商户搜索**：基于 Redis GEO 数据类型实现按距离排序与滚动分页。
- **优惠券秒杀**：Redis + Lua 脚本原子扣减库存，避免超卖。
- **用户认证**：JWT 令牌 + 拦截器校验，通过 ThreadLocal 传递用户上下文。
- **UV 统计**：利用 Redis HyperLogLog 估算独立访客量，内存占用极小。
- **博客社区**：支持发布图文、点赞、评论、关注与 Feed 流推送。

## 📁 项目结构

```
hmdp/
├── hm-dianping/               # Spring Boot 后端
│   ├── src/main/java/com/hmdp/
│   │   ├── controller/        # 接口层
│   │   ├── service/           # 业务逻辑层
│   │   ├── mapper/            # 数据访问层
│   │   ├── entity/            # 实体类
│   │   ├── dto/               # 数据传输对象
│   │   └── utils/             # 工具类（JWT、Redis 等）
│   ├── src/main/resources/
│   │   └── application.yml    # 主配置
│   └── pom.xml
├── nginx-1.18.0/              # Nginx 配置及前端静态文件
│   └── html/hmdp/             # 前端页面
├── Dockefile                  # 自定义镜像
├── docker-compose.yml         # compose一键部署
├── hmdp.sql                   # 数据库初始化脚本
└── README.md
```

## 🚀 快速开始

### 环境准备

- JDK 17+
- MySQL 8.0+
- Redis 7.0+
- Nginx 1.18.0+
- Maven 3.6+
- Docker & Docker Compose (可选)
- Ubuntu 26.01 LTS

### 方式一：使用 Docker Compose（推荐）
```bash
# 克隆项目
git clone https://github.com/orange-orunji/hmdp.git
cd hmdp

# 启动所有服务（MySQL、Redis、后端、Nginx）
docker-compose up -d

# 初始化数据库（首次执行）
docker exec -i mysql-container mysql -uroot -p<YOUR PASSWORD> < hmdp.sql

# 访问 http://localhost

```

### 本地运行

1. **部署mysql**
   ```bash
   mysql -u root -p < hmdp.sql
   ```

2. **修改配置**  
   编辑 `hm-dianping/src/main/resources/application.yml`，填写你的 MySQL 和 Redis 连接信息。

3. **启动后端**
   ```bash
   cd hm-dianping
   mvn spring-boot:run
   ```

4. **配置 Nginx**  
   将前端文件放入 Nginx 的 `html/hmdp/` 目录，添加反向代理配置：
   ```nginx
   location /api {
       proxy_pass http://localhost:8081;
   }
   ```

5. **访问**  
   浏览器打开 `http://localhost` 即可看到前端页面。

## 📊 主要接口

| 模块 | 接口示例 | 说明 |
|------|----------|------|
| 店铺 | `/shop/1` | 查询店铺详情 |
| 附近搜索 | `/shop/of/type?typeId=1&current=1&x=39.9&y=116.4` | 按距离查询周边店铺 |
| 秒杀 | `/voucher-order/seckill/1` | 优惠券秒杀 |
| 登录 | `/user/login` | 手机号验证码登录 |
| 博客 | `/blog/hot?current=1` | 热门博客列表 |

## 📝 更新日志
-  **2026-06-19**:docker一键部署
-  **2026-06-18**:Linux系统部署
-  **2026-06-17**:初始化 README
-  **2026-06-17**:部署到Linux服务器
-  **2026-05-20**:新增 Redis HyperLogLog UV 统计与 GEO 附近店铺滚动查询
-  **2026-05-19**:开发共同关注功能
-  **2026-05-14**:上传核心业务代码
-  **2026-05-09**:项目初始化，提交数据库脚本

## 🔮 未来规划（待升级为高并发深度版）

计划对项目进行架构升级，打造一个能承载高并发、海量数据的企业级点评系统，主要改造方向包括：

1. **Canal 数据同步**  
   监听 MySQL binlog，实时更新 Redis 缓存与 Elasticsearch 索引，解决缓存一致性难题。

2. **多级缓存**  
   引入 Caffeine 本地缓存 + Redis 分布式缓存，配合逻辑过期与布隆过滤器，大幅提升查询性能。

3. **异步队列削峰**  
   接入 RabbitMQ，将秒杀下单异步化，配合死信队列实现重试机制，平滑峰值流量。

4. **分布式锁**  
   使用 Redisson 实现可重入锁，确保一人一单、库存扣减等临界操作的线程安全。

5. **高性能搜索**  
   搭建 Elasticsearch 集群，支持全文检索、地理位置排序、搜索建议等高级功能。

6. **压测与 JVM 调优**  
   通过 JMeter 进行全链路压测，并借助 Arthas 等工具分析热点代码，最终输出 QPS 3000+ 的性能报告。

7. **读写分离与分表**  
   利用 ShardingSphere 实现数据库读写分离、订单分表，应对海量数据存储需求。

---

## 👤 贡献者

- [orange-orunji](https://github.com/orange-orunji)

## 📄 许可证

本项目基于 [MIT License](LICENSE) 开源，修改及分发时请保留原始版权声明。
```

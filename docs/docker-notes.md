# 🐳 知味·本地生活智能推荐平台 - Docker 容器化部署完整实战记录

**部署日期**：2026-06-19  
**项目名称**：知味·本地生活智能推荐 - 本地生活服务点评平台（基于黑马点评）  
**技术栈**：Spring Boot 2.3.12 + Vue + MySQL 8.0 + Redis + Nginx  
**部署方式**：从手动部署演进到 Docker Compose 一键部署  
**部署环境**：Ubuntu 26.04 LTS / VMware 虚拟机

---

## 📑 完整目录

### 第一部分：手动部署踩坑全记录

- [1. 基础环境 & 软件安装](#1-基础环境--软件安装)
- [2. 端口 & 防火墙网络层](#2-端口--防火墙网络层)
- [3. 静态资源层 & 权限](#3-静态资源层--权限)
- [4. 后端服务启动层](#4-后端服务启动层)
- [5. 路径代理与转发层（核心痛点）](#5-路径代理与转发层)

### 第二部分：Docker 容器化部署

- [6. Docker 核心概念速览](#6-docker-核心概念速览)
- [7. Docker 常用命令速查](#7-docker-常用命令速查)
- [8. 数据卷挂载（数据持久化）](#8-数据卷挂载)
- [9. 自定义镜像（Dockerfile）](#9-自定义镜像)
- [10. 容器网络通信](#10-容器网络通信)
- [11. Nginx 反向代理配置](#11-nginx-反向代理配置)
- [12. Docker Compose 完整编排](#12-docker-compose-完整编排)
- [13. 部署流程 & 命令](#13-部署流程--命令)
- [14. 容器化踩坑记录](#14-容器化踩坑记录)
- [15. 最终成果](#15-最终成果)
---

# 第一部分：手动部署踩坑全记录

> **说明**：这部分记录了从零开始手动部署的全部踩坑经历。正是因为经历了这些痛苦，后来学习 Docker 时才更能理解“为什么需要容器化”。

---

## 1. 基础环境 & 软件安装

### 🔴 MySQL 安装冲突
**问题描述**：因先前手动启动过 MySQL 进程，导致 `apt install mysql-server` 时检测到冲突，安装失败。  
**报错信息**：`There is a MySQL server running, but we failed in our attempts to stop it.`

**✅ 解决方案**：
```bash
# 1. 杀掉残留进程
sudo pkill -9 mysql mysqld

# 2. 清理 dpkg 损坏状态
sudo dpkg --configure -a

# 3. 重新安装
sudo apt install mysql-server -y
```

---

### 🔴 Redis 配置文件丢失
**问题描述**：执行 `sudo nano /etc/redis/redis.conf` 打开了一个空白新文件，说明系统没有生成默认配置文件（或路径不对）。  
**现象**：Redis 能启动，但无法通过配置文件修改 `bind` 和 `protected-mode`。

**✅ 解决方案**：
```bash
# 1. 查找 Redis 是否使用了其他配置文件
ps aux | grep redis-server

# 2. 如果没有，手动创建配置文件
sudo nano /etc/redis/redis.conf
```

写入最小配置：
```conf
bind 0.0.0.0
protected-mode no
requirepass  <YOUR MYSQL PASSWORD>
dir /var/lib/redis
dbfilename dump.rdb
```

```bash
# 3. 重启 Redis 并指定配置文件
sudo systemctl stop redis-server
sudo redis-server /etc/redis/redis.conf &
```

---

### 🔴 JDK 下载了 Windows 版
**问题描述**：下载了带 `.exe` 的 Windows 版 JDK 传到 Linux，导致 `java` 命令无法识别。  
**现象**：解压后看到 `java.exe` 和大量 `.dll` 文件。

**✅ 解决方案**：
- 删除错误目录，下载 Linux 版 JDK（`.tar.gz`）
- 或直接用 `apt` 安装：
  ```bash
  sudo apt install openjdk-17-jdk -y
  ```

---

### 🔴 环境变量不生效
**问题描述**：修改了 `/etc/profile` 后执行 `java -version` 仍然找不到。

**✅ 解决方案**：
- 个人配置放 `~/.bashrc`，修改后执行 `source ~/.bashrc`
- 系统级配置需 `sudo vim /etc/profile` 并执行 `source /etc/profile`

---

## 2. 端口 & 防火墙网络层

### 🔴 MySQL 无法远程连接（bind-address）
**问题描述**：`bind-address` 绑死在 `127.0.0.1`，外部机器（Windows）无法连接。  
**报错**：`Can't connect to MySQL server on '192.168.100.128' (10061)`

**✅ 解决方案**：
```bash
# 1. 修改配置文件
sudo nano /etc/mysql/mysql.conf.d/mysqld.cnf
# 注释掉或改为：bind-address = 0.0.0.0

# 2. 重启 MySQL
sudo systemctl restart mysql

# 3. 授权远程用户（在 MySQL 中执行）
CREATE USER 'root'@'%' IDENTIFIED BY '你的密码';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%';
FLUSH PRIVILEGES;
```

---

### 🔴 端口被占用（8081）
**问题描述**：旧的 Java 进程未退出，导致新进程启动失败。  
**报错**：`java.net.BindException: Address already in use`

**✅ 解决方案**：
```bash
# 1. 查看占用端口的进程
sudo lsof -i :8081

# 2. 强制杀掉
sudo kill -9 PID

# 3. 确认端口已释放
sudo ss -tlnp | grep 8081
```

---

### 🔴 Nginx 监听错端口
**问题描述**：配置文件默认监听 `8080`，但浏览器访问默认使用 `80` 端口。  
**现象**：`curl http://localhost:8080` 有响应，但 `http://192.168.100.128` 无响应。

**✅ 解决方案**：
```nginx
# 修改 /etc/nginx/nginx.conf
server {
    listen 80;  # 改为 80
    # ...
}
```
```bash
sudo nginx -t
sudo systemctl reload nginx
```

---

### 🔴 Redis 拒绝连接（protected-mode）
**问题描述**：`redis-cli -h 192.168.100.128 ping` 报错 `DENIED Redis is running in protected mode`。  
**原因**：Redis 开启了保护模式且没有设置密码。

**✅ 解决方案**：
- 在配置文件中设置 `protected-mode no`
- 或设置 `requirepass 密码`
- 重启 Redis：`sudo systemctl restart redis-server`

---

## 3. 静态资源层 & 权限

### 🔴 Nginx 403 权限拒绝
**问题描述**：Nginx 用户（`www-data`）对 `/home/orunji/hmdp` 目录没有访问权限。  
**报错日志**：`open() "/home/orunji/hmdp/index.html" failed (13: Permission denied)`

**✅ 解决方案**：
```bash
# 1. 修改目录所有者和权限
sudo chown -R www-data:www-data /home/orunji/hmdp
sudo chmod -R 755 /home/orunji/hmdp

# 2. 父目录也需有执行权限（否则无法进入）
sudo chmod 755 /home/orunji
```

---

### 🔴 Nginx 404 找不到文件
**问题描述**：上传的前端文件路径多了一层 `nginx-1.18.0/html/`，导致 `root` 指向错误。

**✅ 解决方案**：
```nginx
# 统一 root 路径
root /home/orunji/hmdp;
```
或将文件移动到 `root` 指向的位置。

---

### 🔴 静态资源（CSS/JS）无法加载
**问题描述**：子目录权限未递归设置，导致 CSS/JS 文件返回 403。

**✅ 解决方案**：
```bash
sudo chmod -R 755 /home/orunji/hmdp
```

---

## 4. 后端服务启动层

### 🔴 Jar 包名写错
**问题描述**：执行 `java -jar hmdp-0.0.1-SNAPSHOT.jar` 但实际文件名为 `hm-dianping-0.0.1-SNAPSHOT.jar`。  
**报错**：`Error: Unable to access jarfile`

**✅ 解决方案**：
```bash
# 1. 查看实际文件名
ls *.jar

# 2. 使用正确文件名启动
nohup java -jar hm-dianping-0.0.1-SNAPSHOT.jar &
```

---

### 🔴 配置文件里的 IP 格式错误
**问题描述**：`application.yml` 中 IP 地址写成了 `192:168:100:128`（使用了冒号 `:` 而非点 `.`）。  
**现象**：后端启动后数据库/Redis 连接失败。

**✅ 解决方案**：
```yaml
# 错误写法
url: jdbc:mysql://192:168:100:128:3306/hmdp

# 正确写法
url: jdbc:mysql://192.168.100.128:3306/hmdp
```
修改后重新打包上传。

---

## 5. 路径代理与转发层

> **这是手动部署中最核心、最痛苦的环节。** 前后端路径不一致导致的数据无法显示问题，耗费了最多时间。也正是因为这个环节的痛苦，才凸显了 Docker 容器化的价值。

---

### 🔴 前端请求无 `/api` 前缀
**问题描述**：前端发送 `GET /shop-type/list`，但后端接口路径是 `GET /api/shop-type/list`。  
**现象**：页面显示 `{{t.name}}` 模板变量，数据请求返回 404。

**✅ 解决方案**：
在 Nginx 中添加 location，用 `rewrite` 补上 `/api` 前缀：
```nginx
location ~ ^/(shop-type|blog|shop|voucher|user|follow|comment|upload|admin) {
    rewrite ^/(.*)$ /api/$1 break;
    proxy_pass http://127.0.0.1:8081;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
```

---

### 🔴 Nginx 正则误伤静态文件
**问题描述**：`location ~ ^/(shop|blog|...)` 中的 `shop` 匹配了 `/shop-list.html`，导致静态文件请求被转发到后端，返回 404。  
**现象**：点击分类跳转时，`shop-list.html?type=1` 返回 404。

**✅ 解决方案**：
在正则末尾加上 `/`，仅匹配以这些单词开头且后面紧跟 `/` 的路径（即接口路径）：
```nginx
# 错误写法（会误伤 shop-list.html）
location ~ ^/(shop-type|blog|shop|voucher|user|follow|comment|upload|admin) {

# 正确写法（只匹配接口路径）
location ~ ^/(shop-type|blog|shop|voucher|user|follow|comment|upload|admin)/ {
    rewrite ^/(.*)$ /api/$1 break;
    proxy_pass http://127.0.0.1:8081;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
```

---

### 🔴 前端请求带 `/api`，后端接口不带 `/api`
**问题描述**：登录请求 `POST /api/user/login` 返回 404，但后端实际路径是 `/user/login`。

**✅ 解决方案**：
在 `location /api` 中去掉 `/api` 前缀再转发：
```nginx
location /api {
    rewrite ^/api(/.*)$ $1 break;
    proxy_pass http://127.0.0.1:8081;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
```

---

# 第二部分：Docker 容器化部署

> **说明**：经历了手动部署的所有痛苦后，以下内容是将整个项目迁移到 Docker 容器化的完整记录。

---

## 6. Docker 核心概念速览

| 概念 | 说明 | 类比 |
|------|------|------|
| **镜像（Image）** | 只读的模板，包含运行环境和代码 | 类（Class） |
| **容器（Container）** | 镜像的运行实例 | 对象（Instance） |
| **数据卷（Volume）** | 容器数据的持久化存储 | 外接硬盘 |
| **网络（Network）** | 容器间通信的虚拟交换机 | 局域网 |
| **仓库（Registry）** | 镜像的存储和分发中心 | GitHub |

---

## 7. Docker 常用命令速查

### 7.1 镜像操作

```bash
# 下载镜像
docker pull <镜像名>:<标签>

# 查看本地镜像
docker images

# 删除镜像
docker rmi <镜像ID>

# 构建镜像（基于 Dockerfile）
docker build -t <镜像名> .
```

### 7.2 容器操作

```bash
# 运行容器（后台模式）
docker run -d --name <容器名> <镜像名>

# 查看运行中的容器
docker ps

# 查看所有容器（含已停止）
docker ps -a

# 停止/启动/重启容器
docker stop / start / restart <容器名>

# 删除容器
docker rm -f <容器名>

# 查看容器日志
docker logs <容器名>

# 进入容器内部
docker exec -it <容器名> bash
```

### 7.3 网络操作

```bash
# 创建自定义网络
docker network create <网络名>

# 查看网络详情
docker network inspect <网络名>

# 查看所有网络
docker network ls
```

### 7.4 数据卷操作

```bash
# 创建数据卷
docker volume create <卷名>

# 查看所有数据卷
docker volume ls

# 挂载方式：
# - 命名卷：-v <卷名>:<容器路径>
# - 绑定挂载：-v <宿主机路径>:<容器路径>
```

---

## 8. 数据卷挂载

### 8.1 为什么需要数据卷？

- 容器删除后，内部数据会丢失
- 数据卷让数据独立于容器生命周期
- 对应手动部署时 MySQL 的 `/var/lib/mysql` 目录

### 8.2 两种挂载方式对比

| 挂载方式 | 适用场景 | 示例 | 对应手动部署 |
|----------|----------|------|--------------|
| **命名卷（Volume）** | 数据库数据（MySQL、Redis） | `-v mysql-data:/var/lib/mysql` | 数据存放在 `/var/lib/mysql`，但由 Docker 管理 |
| **绑定挂载（Bind Mount）** | 代码/配置热更新 | `-v ./hmdp:/usr/share/nginx/html` | 前端文件直接放在宿主机 `hmdp` 目录，修改即时生效 |

---

## 9. 自定义镜像

### 9.1 后端 Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY hm-dianping-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 9.2 构建命令

```bash
docker build -t hmdp-backend .
```

### 9.3 关键指令说明

| 指令 | 作用 | 对应手动部署操作 |
|------|------|------------------|
| `FROM` | 指定基础镜像 | 安装 JDK 17 |
| `WORKDIR` | 设置工作目录 | `cd /app` |
| `COPY` | 复制文件到镜像 | 上传 Jar 包 |
| `EXPOSE` | 声明容器监听端口 | 防火墙放行 8081 |
| `ENTRYPOINT` | 容器启动时执行的命令 | `nohup java -jar ... &` |

---

## 10. 容器网络通信

### 10.1 为什么需要自定义网络？

- 容器 IP 会变化，通过 IP 访问不可靠
- 自定义网络支持通过**容器名**互相访问（内置 DNS）
- 对应手动部署时通过 `192.168.100.128` 访问，现在改为通过容器名访问

### 10.2 网络拓扑

```
hmdp-net (bridge)
├── mysql-hmdp   (容器名)
├── redis-hmdp   (容器名)
├── backend      (服务名)
└── nginx-hmdp   (容器名)
```

### 10.3 配置中的体现

```yaml
# application.yml（后端）
spring:
  redis:
    host: redis-hmdp   # 通过容器名访问（取代 IP）
  datasource:
    url: jdbc:mysql://mysql-hmdp:3306/hmdp   # 通过容器名访问
```

```nginx
# nginx.conf（Nginx）
location /api {
    proxy_pass http://backend:8081;   # 通过服务名访问（取代 IP）
}
```

---

## 11. Nginx 反向代理配置

### 11.1 最终 nginx.conf

```nginx
worker_processes 1;

events {
    worker_connections 1024;
}

http {
    include mime.types;
    default_type application/json;
    sendfile on;
    keepalive_timeout 65;

    server {
        listen 80;
        server_name localhost;
        root /usr/share/nginx/html;
        index index.html;

        # 代理带 /api 前缀的请求（去掉前缀）
        location /api {
            rewrite ^/api(/.*)$ $1 break;
            proxy_pass http://backend:8081;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }

        # 代理无 /api 前缀的接口请求（仅匹配带 / 的路径）
        location ~ ^/(shop-type|blog|shop|voucher|user|follow|comment|upload|admin)/ {
            proxy_pass http://backend:8081;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }

        # 静态资源
        location / {
            try_files $uri $uri/ /index.html;
        }
    }
}
```

### 11.2 关键点总结

| 问题 | 解决方案 | 对应手动部署的坑 |
|------|----------|------------------|
| 前端请求 `/api/shop-type/list`，后端只有 `/shop-type/list` | `rewrite ^/api(/.*)$ $1 break;` | 路径前缀不一致 |
| Nginx 误把 `/shop-list.html` 当作 API 代理 | 正则末尾加 `/`：`location ~ ^/(shop-type\|...)/ {` | 静态文件被拦截 |
| Nginx 找不到 `index.html` | `root /usr/share/nginx/html;` | root 路径错误 |
| 后端地址变化 | `proxy_pass http://backend:8081;` | 用容器名取代 IP |

---

## 12. Docker Compose 完整编排

### 12.1 docker-compose.yml

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: mysql-hmdp
    environment:
      MYSQL_ROOT_PASSWORD: <YOUR MYSQL PASSWORD>
      MYSQL_DATABASE: hmdp
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    networks:
      - hmdp-net
    restart: always

  redis:
    image: redis:latest
    container_name: redis-hmdp
    command: redis-server --requirepass  <YOUR REDIS PASSWORD>
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    networks:
      - hmdp-net
    restart: always

  backend:
    build: ./hm-dianping
    container_name: backend-hmdp
    ports:
      - "8081:8081"
    depends_on:
      - mysql
      - redis
    environment:
      SPRING_DATASOURCE_URL: "jdbc:mysql://mysql:3306/hmdp?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD:  <YOUR MYSQL PASSWORD>
      SPRING_REDIS_HOST: redis
      SPRING_REDIS_PORT: 6379
    networks:
      - hmdp-net
    restart: always

  nginx:
    image: nginx:latest
    container_name: nginx-hmdp
    ports:
      - "80:80"
    volumes:
      - ./hmdp:/usr/share/nginx/html
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      - backend
    networks:
      - hmdp-net
    restart: always

volumes:
  mysql-data:
  redis-data:

networks:
  hmdp-net:
```

---

## 13. 部署流程 & 命令

### 13.1 项目目录结构

```
~/ (家目录)
├── docker-compose.yml      # 服务编排文件
├── nginx.conf              # Nginx 配置文件
├── hmdp/                   # 前端静态文件
│   ├── index.html
│   ├── css/
│   ├── js/
│   └── imgs/
├── hm-dianping/            # 后端项目
│   ├── Dockerfile
│   └── hm-dianping-0.0.1-SNAPSHOT.jar
└── hmdp.sql                # 数据库初始化脚本
```

### 13.2 启动命令

```bash
# 一键启动所有服务
docker compose up -d

# 查看服务状态
docker compose ps

# 查看实时日志
docker compose logs -f

# 查看某个服务的日志
docker compose logs -f backend

# 停止所有服务
docker compose down

# 停止并删除数据卷（慎用，会丢失数据）
docker compose down -v
```

### 13.3 数据初始化（仅首次部署）

```bash
# 1. 将 SQL 文件复制到 MySQL 容器
docker cp ~/hmdp.sql mysql-hmdp:/tmp/hmdp.sql

# 2. 导入数据
docker exec -i mysql-hmdp mysql -u root -p <YOUR MYSQL PASSWORD> < ~/hmdp.sql

# 3. 验证数据
docker exec -it mysql-hmdp mysql -u root -p -e "SELECT COUNT(*) FROM hmdp.tb_shop;"
# 输入密码后，应返回 14（店铺数量）
```

### 13.4 验证服务

```bash
# 测试后端 API（通过 Nginx 代理）
curl http://localhost/api/shop-type/list

# 浏览器访问
http://<虚拟机IP>
```

---

## 14. 容器化踩坑记录

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| Redis 连接失败 | Redisson 硬编码 `localhost` | 修改 `RedisConfig.java`，改为 `redis://redis-hmdp:6379`，并设置密码 |
| Nginx 500（rewrite cycle） | `root` 路径错误 | 改为容器内路径 `/usr/share/nginx/html` |
| `/shop-list.html` 返回 404 | 正则误匹配静态文件 | 在 `location ~` 末尾加 `/` |
| Nginx 无法解析 `backend-hmdp` | 服务名不是容器名 | 使用 Compose 服务名 `backend`，或指定 `container_name` |
| `Unknown database 'hmdp'` | 数据卷为空，未导入 SQL | 执行 `docker cp` + 导入 SQL |
| 502 Bad Gateway | 后端启动慢，Nginx 先启动 | 等待后端就绪，或 `docker compose restart nginx` |

---

## 15. 最终成果

| 服务 | 容器名 | 状态 | 数据持久化 |
|------|--------|-----|----------|
| MySQL 8.0 | mysql-hmdp |  运行中 |  mysql-data 卷（含 14 条店铺数据） |
| Redis latest | redis-hmdp |  运行中 |  redis-data 卷 |
| Spring Boot 后端 | backend-hmdp |  运行中 | 环境变量覆盖配置 |
| Nginx | nginx-hmdp |  运行中 |  绑定挂载前端文件 + 配置文件 |
| 网络 | hmdp-net |  已创建 |  所有容器通过容器名互联 |
| 一键启动 | docker compose up -d |  可用 |  单命令启动全部服务 |

---

# 第三部分：总结与展望

## 16. 掌握能力

-  能独立编写 `Dockerfile` 打包 Spring Boot 项目
-  能使用 `docker run` 启动 MySQL、Redis、Nginx 等基础服务
-  能配置自定义网络，让容器间通过名字互相访问
-  能使用数据卷实现数据持久化和代码热更新
-  能排查容器启动失败的问题（日志、网络、配置三板斧）
-  能修改 Nginx 配置解决前后端路径不匹配的问题
-  能使用 `docker-compose.yml` 一键启动整个项目
-  能从零开始将手动部署项目迁移到容器化部署

---


**文档版本**：v1.0  
**最后更新**：2026-06-19  
**作者**：orunji

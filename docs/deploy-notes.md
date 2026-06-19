# Linux 部署黑马点评 - 踩坑记录

**日期**: 2026-06-17  
**部署人**: orange-orunji  
**项目**: 黑马点评（hmdp）  
**环境**: Ubuntu 26.04 LTS  
**部署方式**: 手动部署（非容器化）

---

## 📌 目录

1. [基础环境 & 软件安装层](#1-基础环境--软件安装层)
2. [端口 & 防火墙网络层](#2-端口--防火墙网络层外部访问不了)
3. [静态资源层 & 权限](#3-静态资源层--权限)
4. [后端服务启动层](#4-后端服务启动层后端-java-跑不起来)
5. [路径代理与转发层（核心痛点）](#5-路径代理与转发层前后端数据对不上今天最核心最长的拉锯战)

---

## 1. 基础环境 & 软件安装层

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
requirepass 你的密码
dir /var/lib/redis
dbfilename dump.rdb
```

```bash
# 3. 重启 Redis 并指定配置文件
sudo systemctl stop redis-server
sudo redis-server /etc/redis/redis.conf &

# 4. 或修改 systemd 服务文件指定配置路径
sudo systemctl edit --full redis-server
# 修改 ExecStart 为：/usr/bin/redis-server /etc/redis/redis.conf
sudo systemctl daemon-reload
sudo systemctl restart redis-server
```

---

## 2. 端口 & 防火墙网络层（“外部访问不了”）

### 🔴 MySQL 无法远程连接
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
**现象**：文件明明在 `/home/orunji/hmdp/nginx-1.18.0/html/hmdp/` 下，但 Nginx 的 `root` 指向 `/home/orunji/hmdp`。

**✅ 解决方案**：
```nginx
# 方案 A：修改 root 指向文件实际所在目录
root /home/orunji/hmdp/nginx-1.18.0/html/hmdp;

# 方案 B：将文件移动到 root 指向的目录（推荐，路径更简洁）
sudo mv /home/orunji/hmdp/nginx-1.18.0/html/hmdp/* /home/orunji/hmdp/
root /home/orunji/hmdp;
```
```bash
sudo nginx -t
sudo systemctl reload nginx
```

---

### 🔴 静态资源（CSS/JS）无法加载
**问题描述**：子目录权限未递归设置，导致 CSS/JS 文件返回 403。  
**现象**：页面有内容但无样式，F12 查看 CSS/JS 请求返回 403。

**✅ 解决方案**：
```bash
# 递归设置目录权限
sudo chmod -R 755 /home/orunji/hmdp
```

---

## 4. 后端服务启动层（“后端 Java 跑不起来”）

### 🔴 Jar 包名写错
**问题描述**：执行 `java -jar hmdp-0.0.1-SNAPSHOT.jar` 但实际文件名为 `hm-dianping-0.0.1-SNAPSHOT.jar`。  
**报错**：`Error: Unable to access jarfile hmdp-0.0.1-SNAPSHOT.jar`

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
**现象**：后端启动后数据库/Redis 连接失败，日志显示 `Communications link failure`。

**✅ 解决方案**：
```yaml
# 错误写法
url: jdbc:mysql://192:168:100:128:3306/hmdp

# 正确写法
url: jdbc:mysql://192.168.100.128:3306/hmdp
```
修改后重新打包上传：
```bash
mvn clean package -DskipTests
scp target/hm-dianping-0.0.1-SNAPSHOT.jar orunji@192.168.100.128:~
```

---

## 5. 路径代理与转发层（“前后端数据对不上”——今天最核心、最长的拉锯战）

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

### 🔴 Nginx 代理加前缀（续）
**问题描述**：上述配置中 `rewrite ^/(.*)$ /api/$1 break;` 的作用是将 `/shop-type/list` 重写为 `/api/shop-type/list`，再转发给后端。  
**核心原理**：`rewrite` 改写请求路径，`break` 停止后续匹配。

---

### 🔴 Nginx 正则误伤静态文件
**问题描述**：`location ~ ^/(shop|blog|...)` 中的 `shop` 匹配了 `/shop-list.html`，导致静态文件请求被转发到后端，返回 404。  
**现象**：点击分类跳转时，`shop-list.html?type=1` 返回 404，而不是页面。

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

## 🎯 最终完整 Nginx 配置（关键部分）

```nginx
server {
    listen 80;
    server_name localhost;
    root /home/orunji/hmdp;
    index index.html;

    # 1. 无前缀接口请求 → 补上 /api 再转发
    location ~ ^/(shop-type|blog|shop|voucher|user|follow|comment|upload|admin)/ {
        rewrite ^/(.*)$ /api/$1 break;
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # 2. 带 /api 前缀的请求 → 去掉前缀再转发
    location /api {
        rewrite ^/api(/.*)$ $1 break;
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # 3. 静态资源
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

---

## 📊 总结：今天学会的“三板斧”

| 问题类型 | 排查命令 | 解决方向 |
| :--- | :--- | :--- |
| **服务起不来** | `tail -50 nohup.out` / `journalctl -u xxx` | 看日志，找异常堆栈 |
| **连不上/被拒绝** | `ss -tlnp \| grep 端口` / `lsof -i :端口` | 看监听地址和占用进程 |
| **路径/权限错误** | `ls -la` / `tail -20 /var/log/nginx/error.log` | 看权限和文件是否存在 |

---

## 🏆 最终成果

- ✅ 所有服务均已部署并正常运行  
- ✅ 前后端请求路径完全对齐  
- ✅ 页面数据正常显示，登录、店铺列表、博客等核心功能可用  
- ✅ Nginx 监听 80 端口，提供静态资源服务，并代理所有接口请求到后端 8081 端口  

---

> 💡 **一句话心得**：Linux 下的一切问题，最终都能在**日志、端口、路径**这三者中找到答案。  
```

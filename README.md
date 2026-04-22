# CampusOrder校园订餐系统

## 项目介绍

CampusOrder是一个面向校园场景的订餐系统，提供用户管理、店铺管理、优惠券管理、订单管理等功能，支持高并发下的秒杀活动和支付功能。

## 技术栈

- **后端框架**：Spring Boot 3.3.4
- **持久层**：MyBatis-Plus 3.4.3
- **数据库**：MySQL
- **缓存**：Redis
- **消息队列**：Kafka
- **分布式锁**：Redisson
- **工具库**：Hutool

## 功能模块

### 1. 用户模块
- 用户注册与登录
- 手机验证码发送
- 用户信息管理
- 每日签到
- 签到统计

### 2. 店铺模块
- 店铺信息管理
- 店铺类型管理

### 3. 优惠券模块
- 优惠券管理
- 秒杀优惠券
- 优惠券订单

### 4. 博客模块
- 博客发布与管理
- 博客评论

## 快速开始

### 环境要求
- JDK 1.8+
- MySQL 5.7+
- Redis 6.0+
- Kafka 2.8.0+

### 安装步骤

1. **克隆项目**
```bash
git clone https://github.com/yourusername/CampusOrder.git
cd CampusOrder
```

2. **配置数据库**
- 创建数据库 `campus_order`
- 执行 `src/main/resources/db/hmdp.sql` 初始化数据库

3. **配置Redis**
- 确保Redis服务已启动
- 在 `application.yaml` 中配置Redis连接信息

4. **配置Kafka**
- 确保Kafka服务已启动
- 在 `application.yaml` 中配置Kafka连接信息

5. **启动项目**
```bash
mvn spring-boot:run
```

## 项目结构

```
├── src/
│   ├── main/
│   │   ├── java/com/campusOrder/
│   │   │   ├── config/         # 配置类
│   │   │   ├── controller/     # 控制器
│   │   │   ├── dto/            # 数据传输对象
│   │   │   ├── entity/         # 实体类
│   │   │   ├── mapper/         # MyBatis映射器
│   │   │   ├── service/        # 服务层
│   │   │   │   └── impl/       # 服务实现
│   │   │   ├── utils/          # 工具类
│   │   │   └── CampusOrderApplication.java  # 应用入口
│   │   └── resources/
│   │       ├── db/             # 数据库脚本
│   │       ├── mapper/         # MyBatis XML映射文件
│   │       ├── application.yaml # 应用配置
│   │       ├── seckill.lua     # 秒杀脚本
│   │       └── unlock.lua      # 解锁脚本
│   └── test/                   # 测试类
├── pom.xml                     # Maven配置
└── README.md                   # 项目说明
```

## API接口

### 用户相关
- `POST /user/code` - 发送验证码
- `POST /user/login` - 用户登录
- `POST /user/logout` - 用户登出
- `GET /user/me` - 获取当前用户信息
- `GET /user/info/{id}` - 获取用户详情
- `GET /user/{id}` - 根据ID查询用户
- `POST /user/sign` - 每日签到
- `GET /user/sign/count` - 获取签到统计

### 关注相关
- `PUT /follow/{id}/{isFollow}` - 关注/取消关注
- `GET /follow/or/not/{id}` - 检查是否关注
- `GET /follow/common/{id}` - 获取共同关注

### 优惠券相关
- `POST /voucher-order/seckill/{id}` - 秒杀优惠券
- `POST /voucher-order/pay/{id}` - 支付优惠券订单

## 核心功能实现

### 1. 秒杀功能
- 使用Redis + Lua脚本实现高性能秒杀
- 使用Kafka实现异步订单处理
- 使用Redisson实现分布式锁防止重复下单

### 2. 签到功能
- 使用Redis的BitMap实现高效签到统计
- 支持连续签到天数计算

### 3. 关注功能
- 使用Redis的Set集合实现关注关系管理
- 支持共同关注好友查询

## 注意事项

1. 项目使用了Redis和Kafka，确保这些服务已正确配置和启动
2. 秒杀功能需要在Redis中预加载优惠券库存
3. 项目使用了JWT进行用户认证，请确保在请求头中携带有效的token

## 许可证

本项目供内部学习使用

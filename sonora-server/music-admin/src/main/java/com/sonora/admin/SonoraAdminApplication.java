package com.sonora.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sonora Music — 服务端启动入口
 * <p>
 * 自动扫描以下模块的组件:
 * - sonora-admin (本模块)
 * - sonora-common (统一响应、异常处理)
 * - sonora-model (实体类)
 * - sonora-mapper (数据访问)
 * - sonora-service (业务逻辑)
 * - sonora-security (安全认证)
 * - sonora-file (文件服务)
 * - sonora-client (客户端 API)
 */
@SpringBootApplication(scanBasePackages = "com.sonora")
public class SonoraAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(SonoraAdminApplication.class, args);
    }
}

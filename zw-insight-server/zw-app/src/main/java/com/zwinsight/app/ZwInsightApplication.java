package com.zwinsight.app;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ZW Insight 工程项目管理平台启动类
 */
@SpringBootApplication(scanBasePackages = "com.zwinsight")
// 各业务模块的 MyBatis Mapper 分布在 com.zwinsight.*.mapper 包；
// 无显式 MapperScan 时 MyBatis 仅扫描主类所在包(com.zwinsight.app)导致 Mapper 无法注册。
// 限定 annotationClass = Mapper.class，仅注册带 @Mapper 的接口，避免误扫普通接口。
// 跨模块存在同名 Mapper（如 SysUserProjectMapper、BizOfficeSupplyMapper），
// 用全限定名作为 Bean 名避免名称冲突（Mapper 均按类型注入，不依赖 Bean 名）。
@MapperScan(
        basePackages = "com.zwinsight",
        annotationClass = Mapper.class,
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class
)
@EnableScheduling
public class ZwInsightApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZwInsightApplication.class, args);
    }

}

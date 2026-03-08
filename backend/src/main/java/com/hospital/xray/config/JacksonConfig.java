package com.hospital.xray.config;

import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局配置
 * 注意：Long→String 的全局转换已移除，因为它会将 totalCases/totalReports 等小整数
 * 也转为字符串，导致前端统计页面的算术运算（reduce/percent）出错。
 * Snowflake ID 精度丢失问题由前端 transformResponse 正则（\d{16,}）处理。
 */
@Configuration
public class JacksonConfig {
}

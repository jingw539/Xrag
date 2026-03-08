package com.hospital.xray.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * HTTP 请求工具类
 * 用于获取当前请求的相关信息
 */
public class RequestUtils {
    
    /**
     * 获取当前请求对象
     * 
     * @return HttpServletRequest 或 null
     */
    public static HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = 
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
    
    /**
     * 获取客户端IP地址
     * 支持通过代理获取真实IP
     * 
     * @return 客户端IP地址
     */
    public static String getClientIp() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return "unknown";
        }
        
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // 对于多个代理的情况，第一个IP为客户端真实IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }
    
    /**
     * 获取请求路径
     * 
     * @return 请求路径
     */
    public static String getRequestPath() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return "";
        }
        return request.getRequestURI();
    }
    
    /**
     * 获取请求方法
     * 
     * @return 请求方法（GET, POST, PUT, DELETE等）
     */
    public static String getRequestMethod() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return "";
        }
        return request.getMethod();
    }
}

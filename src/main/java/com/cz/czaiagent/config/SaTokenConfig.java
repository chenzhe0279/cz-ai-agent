package com.cz.czaiagent.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 拦截器配置：
 * - /user/** 与 /ai/manus/** 接口需要登录（注册、登录接口除外）；/ai/love_app/** 允许游客访问
 * - 管理员角色校验通过 @SaCheckRole 注解在 Controller 上声明
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
                    // 放行 CORS 预检请求（OPTIONS），预检请求不会携带令牌
                    if ("OPTIONS".equalsIgnoreCase(SaHolder.getRequest().getMethod())) {
                        return;
                    }
                    StpUtil.checkLogin();
                }))
                .addPathPatterns("/user/**", "/ai/manus/**")
                .excludePathPatterns("/user/register", "/user/register/code", "/user/login/**", "/user/password/**");
    }
}

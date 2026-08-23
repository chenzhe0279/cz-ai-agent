package com.cz.czaiagent.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.SaTokenContextException;
import cn.dev33.satoken.jwt.StpLogicJwtForStateless;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 拦截器配置：
 * - /user/** 与 /ai/manus/** 接口需要登录（注册、登录接口除外）；/ai/love_app/** 允许游客访问
 * - 管理员角色校验通过 @SaCheckRole 注解在 Controller 上声明
 * - 采用 JWT 无状态模式：登录态由令牌自身携带（含过期时间），不依赖后端内存会话，
 *   因此页面刷新、后端重启后登录态依然有效
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 注册无状态 JWT 的 StpLogic 实现。
     * 注意：本应用自定义了 StpInterfaceImpl（角色数据源），会破坏 Sa-Token 的
     * “约定优于配置”自动装配，必须显式注入该 Bean，JWT 模式才会生效。
     */
    @Bean
    public StpLogic getStpLogicJwt() {
        return new StpLogicJwtForStateless();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
                    // 放行 CORS 预检请求（OPTIONS），预检请求不会携带令牌
                    try {
                        if ("OPTIONS".equalsIgnoreCase(SaHolder.getRequest().getMethod())) {
                            return;
                        }
                    } catch (SaTokenContextException e) {
                        // SSE 等异步请求收尾阶段会重新派发（async dispatch），此时 Sa-Token 上下文
                        // （ThreadLocal）不会被 OncePerRequestFilter 重新初始化；登录态已在首次请求校验过，直接放行
                        return;
                    }
                    StpUtil.checkLogin();
                }))
                .addPathPatterns("/user/**", "/ai/manus/**")
                .excludePathPatterns("/user/register", "/user/register/code", "/user/login/**", "/user/password/**");
    }
}

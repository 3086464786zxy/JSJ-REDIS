package com.itmk.config.security;

import com.itmk.config.security.detailservice.CustomerUserDetailService;
import com.itmk.config.security.filter.CheckTokenFilter;
import com.itmk.config.security.handler.CustomAccessDeineHandler;
import com.itmk.config.security.handler.LoginFailureHandler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * @Configuration:
 * 表明SpringSecurityConfig类是一个配置类 @EnableWebSecurity：启动springsecurity @EnableMethodSecurity :
 * 启用方法级权限注解
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SpringSecurityConfig {
    @Autowired private CustomerUserDetailService customerUserDetailService;
    @Autowired private LoginFailureHandler loginFailureHandler;
    @Autowired private CustomAccessDeineHandler customAccessDeineHandler;
    @Autowired private CheckTokenFilter checkTokenFilter;
    @Autowired private com.itmk.config.security.filter.BrowserRequestGuardFilter browserGuard;

    @Bean
    public FilterRegistrationBean<com.itmk.config.security.filter.BrowserRequestGuardFilter>
            disableBrowserGuardAutoRegistration() {
        var registration = new FilterRegistrationBean<>(browserGuard);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 该过滤器只加入 SecurityFilterChain，禁止容器再次自动注册。 */
    @Bean
    public FilterRegistrationBean<CheckTokenFilter> disableTokenFilterAutoRegistration() {
        FilterRegistrationBean<CheckTokenFilter> registration =
                new FilterRegistrationBean<>(checkTokenFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 配置token过滤器
        http.addFilterBefore(checkTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(browserGuard, CheckTokenFilter.class)
                // 解决跨域
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .headers(
                        (headers) ->
                                headers.frameOptions((HeadersConfigurer.FrameOptionsConfig::deny)))
                // 无状态
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 鉴权白名单, 配置绕过鉴权的接口
                .authorizeHttpRequests(
                        (authorized) ->
                                authorized
                                        // 这里过滤一些 不需要token的接口地址
                                        .requestMatchers(
                                                "/api/sysUser/getImage",
                                                "/api/sysUser/login",
                                                "/api/refresh",
                                                "/api/sysUser/loginOut",
                                                "/images/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                // 指定 登录鉴权时 查询用户信息的实现类
                .userDetailsService(customerUserDetailService)
                // 自定义异常处理
                .exceptionHandling(
                        (exceptionHandling) ->
                                exceptionHandling
                                        .authenticationEntryPoint(loginFailureHandler) // 匿名处理
                                        .accessDeniedHandler(customAccessDeineHandler) // 无权限
                        );
        // 构建过滤链并返回
        return http.build();
    }

    // 注入AuthenticationManager
    @Bean
    public AuthenticationManager authenticationManagerBean(
            AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}

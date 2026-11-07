package com.yijia.config;

import com.yijia.service.IUVService;
import com.yijia.utils.LoginInterceptor;
import com.yijia.utils.RefreshTokenInterceptor;
import com.yijia.utils.WriteAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUVService uvService;

    @Override
    public void addInterceptors(InterceptorRegistry registry){
        //注册拦截器
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns("/user/code"
                        ,"/user/login"
                        , "/provider/**"
                        ,"/service-type/**"
                        ,"/upload/**"
                        ,"/note/hot"
                        ,"/deal/**").order(2);
        //刷新token拦截器（顺带记录 UV）
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate, uvService))
                .addPathPatterns("/**").order(0);
        //写操作兜底校验：白名单只放行浏览，POST/PUT/DELETE 必须登录
        registry.addInterceptor(new WriteAuthInterceptor())
                .addPathPatterns("/**").order(1);
    }

}

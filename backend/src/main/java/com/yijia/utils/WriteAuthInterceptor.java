package com.yijia.utils;

import com.yijia.dto.UserDTO;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 写操作强制登录。
 *
 * <p>{@code excludePathPatterns} 只能按路径排除，没法区分 GET 和 POST，所以
 * {@code /provider/**}、{@code /deal/**} 这类"浏览接口白名单"会把创建/修改接口一起放行。
 * 这个拦截器在 token 刷新之后兜底：凡是 POST/PUT/DELETE，除登录注册外一律要求已登录。
 */
public class WriteAuthInterceptor implements HandlerInterceptor {

    /** 允许匿名调用的写接口 */
    private static final Set<String> ANONYMOUS_WRITE = new HashSet<>(Arrays.asList(
            "/user/code",
            "/user/login"
    ));

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        if (ANONYMOUS_WRITE.contains(request.getRequestURI())) {
            return true;
        }
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            response.setStatus(401);
            return false;
        }
        return true;
    }
}

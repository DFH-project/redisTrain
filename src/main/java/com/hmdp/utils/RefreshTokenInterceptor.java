package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.SystemConstants.Redis_Login_TOKEN_PREFIX;

// 拦截所有  执行token刷新
public class RefreshTokenInterceptor implements HandlerInterceptor {


    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 先获取session
//        HttpSession session = request.getSession();
//        Object user = session.getAttribute("user");
        // 获取token
        String token = request.getHeader("authorization");
        if (StringUtils.isBlank(token)) {
            return true;
        }
        // 从redis 取出用户
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(Redis_Login_TOKEN_PREFIX+token);
        if (entries.isEmpty()) {
            return true;
        }
        UserDTO userDto = BeanUtil.fillBeanWithMap(entries, new UserDTO(), false);
        // ThreadLocal 存储 userdto
        UserHolder.saveUser(userDto);
        // 刷新token
        stringRedisTemplate.expire(Redis_Login_TOKEN_PREFIX+token,30, TimeUnit.MINUTES);
        // 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}

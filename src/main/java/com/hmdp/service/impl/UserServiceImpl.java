package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.StringUtils;
import com.hmdp.utils.UserHolder;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.SystemConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

//    @Resource
//    private RedisTemplate redisTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1、 检验手机号
        if (RegexUtils.isPhoneInvalid(phone))  return Result.fail("手机号格式不正确");
        // 2. 生成 code
        String code = RandomUtil.randomNumbers(6).toString();
        // 3. 保存到session
        //session.setAttribute("code",code);
        // 3. 使用redis ，不再使用session  有效期为两分钟
        stringRedisTemplate.opsForValue().set(Redis_Login_PREFIX+phone,code,2, TimeUnit.MINUTES);
        // 4. 调用短信接口
        System.out.println("code=="+code);
        return Result.ok();
    }

    @Override
    public Result loginSys(LoginFormDTO loginForm, HttpSession session) {
        // 检验手机号
        if (StringUtils.isEmpty(loginForm.getPhone())){
           return Result.fail("手机号不能为空");
        }
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone))  return Result.fail("手机号格式不正确");
        // 校验验证码
//        Object cacheCode = session.getAttribute("code");
        // 校验验证码 从redis 获取
        String cacheCode = stringRedisTemplate.opsForValue().get(Redis_Login_PREFIX + phone);
        if (cacheCode  == null  || !cacheCode.equals(loginForm.getCode())){
            // 验证码过期
            return Result.fail("验证码错误");
        }

        // 验证码一致
        User one = query().eq("phone", phone).one();
        if (one == null){
            // 用户不存在 ,注册新用户
            one = creatUserWithPhone(phone);
        }
        // 保存用户到session
        // session.setAttribute("user", BeanUtil.copyProperties(one, UserDTO.class));
        // 保存用户到redis
        // TODO 先生成随机token
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(one, UserDTO.class);
        Map<String, Object> map = BeanUtil.beanToMap(userDTO , new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((name ,value)->value.toString()));
        stringRedisTemplate.opsForHash().putAll(Redis_Login_TOKEN_PREFIX+token,map);
        stringRedisTemplate.expire(Redis_Login_TOKEN_PREFIX+token,30,TimeUnit.MINUTES);
        return Result.ok(token);
    }

    @Override
    public User creatUserWithPhone(String phone) {
        User one = new User();
        one.setPhone(phone);
        one.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(5));
        save(one);
        return one;
    }

    @Override
    public Result me() {
        // 获取当前登录用户并返回
        UserDTO userDTO = UserHolder.getUser();
        if (StringUtils.isNull(userDTO)){
            return Result.fail("用户未登录");
        }
        return Result.ok(userDTO);
    }


    @Override
    public Result logout() {
        UserHolder.removeUser();
        return null;
    }
}

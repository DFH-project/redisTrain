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
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.USER_SIGN_KEY;
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

    @Override
    public Result userSign() {
        // 先获取当前用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        // 获取当前日期
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        int dayOfMonth = now.getDayOfMonth();
        String key = USER_SIGN_KEY+user.getId()+keySuffix;
        // 判断
        if (stringRedisTemplate.opsForValue().getBit(key,dayOfMonth-1)){
            return Result.ok("用户已签到");
        }
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);
        return Result.ok();
    }

    @Override
    public Result userSignCount() {
        // 先获取当前用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }

        // 获取当前日期
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        int dayOfMonth = now.getDayOfMonth();
        String key = USER_SIGN_KEY+user.getId()+keySuffix;
        // 获取签到记录
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create().
                get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (result.size()==0) {
            return Result.ok(0);
        }
        // 获取当前用户签到天数
        Long num = result.get(0);
        if (num == null || num == 0) return Result.ok(0);
        // 循环
        int count = 0 ;
        while (true){
            if ((num & 1) == 0){
                break;
            }else{
                count++;
                //num = num >> 1;
                num >>>= 1 ;  // 无符号右移  >> 是有符号右移操作符，而 >>> 是无符号右移操作符
            }
        }
        return Result.ok(count);
    }
}

package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.StringUtils;
import io.netty.util.internal.StringUtil;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.UUID;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

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

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1、 检验手机号
        if (RegexUtils.isPhoneInvalid(phone))  return Result.fail("手机号格式不正确");
        // 2. 生成 code
        String code = RandomUtil.randomNumbers(6).toString();
        // 3. 保存到session
        session.setAttribute("code",code);
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
        Object cacheCode = session.getAttribute("code");
        if (cacheCode  == null  || !cacheCode.toString().equals(loginForm.getCode())){
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
        session.setAttribute("user",one);
        return Result.ok();
    }

    @Override
    public User creatUserWithPhone(String phone) {
        User one = new User();
        one.setPhone(phone);
        one.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(5));
        save(one);
        return one;
    }
}

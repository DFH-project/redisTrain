package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hmdp.utils.RedisConstants.Follow_User_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long id, boolean isFollow) {
        // 登录用户
        Long userId = UserHolder.getUser().getId();
        String key = Follow_User_KEY + userId;

        if (isFollow){
            //关注 新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            boolean save = save(follow);
            if (save){
                stringRedisTemplate.opsForSet().add(key , id.toString());
            }
        }else{
            // 取消关注
            boolean remove = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", id));
            if (remove){

                stringRedisTemplate.opsForSet().remove(key , id.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long id) {
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("follow_user_id", id).count();
        return Result.ok(count>0);
    }

    @Override
    public Result commonFlow(Long id) {
        //  获取当前用户的关注
        Long userId = UserHolder.getUser().getId();
        String key1 = Follow_User_KEY + userId;
        // 关注的用户
        String key2 = Follow_User_KEY + id;
        // 交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect ==null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> commonList = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<User> users = userService.listByIds(commonList);
        Stream<UserDTO> dtoStream = users.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class));
        return Result.ok(dtoStream);
    }
}

package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;


    @Override
    public Result queryInfoById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("不存在");
        }
        extracted(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(this::extracted);
        return Result.ok(records);
    }

    @Override
    public Result liked(Long id) {
        // 获取登录用户
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        // 先查看Redis
        // Boolean member = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null){
            // 不存在 没点过赞
            boolean succ = update().setSql("liked = liked + 1").eq("id", id).update();
            if (succ){
                // stringRedisTemplate.opsForSet().add(key,userId.toString());
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }else{
            boolean succ = update().setSql("liked = liked - 1").eq("id", id).update();
            if (succ){
                //  stringRedisTemplate.opsForSet().remove(key,userId.toString());
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }
        return null;
    }

    @Override
    public Result queryLikesBlog(Long id) {
        // 实现查询 TOP5
        String key =  BLOG_LIKED_KEY + id;
        Set<String> userIdSet = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (userIdSet==null || userIdSet.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> list = userIdSet.stream().map(Long::valueOf).collect(Collectors.toList());
        //List<User> users = userService.listByIds(list);  // 使用 in 但是不会按照顺序查出，需要拼接成 order by Filed (id , 5 ,1  )
        String idStr = StrUtil.join(",",list);
        List<User> users = userService.query().in("is",list).last("order by Field ( id ,"+idStr +")").list();
        List<UserDTO> userDTOS = users.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean save = save(blog);
        // 查询笔记作者所有粉丝
        if (!save){
             return Result.fail("保存失败");
        }
        List<Follow> flowUser = followService.query().eq("flow_user_id", user.getId()).list();
        // 开始推送给粉丝
        for (Follow follow:flowUser) {
            // 获取粉丝ID
            Long id = follow.getUserId();
            String key = FEED_KEY + id ;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long lastId, Integer offset) {
        // 获取当前用户收件箱
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + userId;
        // 查询收件箱  滚动分页查询  ZREVANGEBYSCORE KEY MAX MIN LIMIT OFFSET COUNT
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, lastId, offset, 3);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        // 解析数据  blogId  、 score（时间戳 最小的）、offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int offsetNum = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            // 获取 id
            ids.add(Long.valueOf(typedTuple.getValue()));
            if (typedTuple.getScore().longValue() == minTime){
                offsetNum++;
            }else{
                minTime = typedTuple.getScore().longValue();
                offsetNum=1;
            }
        }
        // 查询 blog  listByIds 是基于MySQL的in ，会打乱顺序。
        String idStr = StrUtil.join(",",ids);
        List<Blog> blogs = query().in("id",ids).last("order by Field (id , "+idStr+")").list();
        blogs.forEach(this::extracted);
        // 封装返回
        ScrollResult scrollResult =new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(offsetNum);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }

    private void extracted(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        isLiked(blog);

    }
    public void isLiked(Blog blog){
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null ){
            return;
        }
        Long userId = userDTO.getId();
        Double member = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + blog.getId(), userId.toString());
        blog.setIsLike(member == null?false:true);

    }



}

package com.campusOrder.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campusOrder.dto.Result;
import com.campusOrder.dto.ScrollResult;
import com.campusOrder.dto.UserDTO;
import com.campusOrder.entity.Blog;
import com.campusOrder.entity.Follow;
import com.campusOrder.entity.User;
import com.campusOrder.mapper.BlogMapper;
import com.campusOrder.service.IBlogService;
import com.campusOrder.service.IFollowService;
import com.campusOrder.service.IUserService;
import com.campusOrder.utils.SystemConstants;
import com.campusOrder.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.campusOrder.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.campusOrder.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 * 鏈嶅姟瀹炵幇绫?
 * </p>
 *
 * @author 铏庡摜
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
    public Result queryHotBlog(Integer current) {
        // 鏍规嵁鐢ㄦ埛鏌ヨ
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 鑾峰彇褰撳墠椤垫暟鎹?
        List<Blog> records = page.getRecords();
        // 鏌ヨ鐢ㄦ埛
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        // 1.鏌ヨblog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("绗旇涓嶅瓨鍦紒");
        }
        // 2.鏌ヨblog鏈夊叧鐨勭敤鎴?
        queryBlogUser(blog);
        // 3.鏌ヨblog鏄惁琚偣璧?
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        // 1.鑾峰彇鐧诲綍鐢ㄦ埛
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 鐢ㄦ埛鏈櫥褰曪紝鏃犻渶鏌ヨ鏄惁鐐硅禐
            return;
        }
        Long userId = user.getId();
        // 2.鍒ゆ柇褰撳墠鐧诲綍鐢ㄦ埛鏄惁宸茬粡鐐硅禐
        String key = "blog:liked:" + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result likeBlog(Long id) {
        // 1.鑾峰彇鐧诲綍鐢ㄦ埛
        Long userId = UserHolder.getUser().getId();
        // 2.鍒ゆ柇褰撳墠鐧诲綍鐢ㄦ埛鏄惁宸茬粡鐐硅禐
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            // 3.濡傛灉鏈偣璧烇紝鍙互鐐硅禐
            // 3.1.鏁版嵁搴撶偣璧炴暟 + 1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 3.2.淇濆瓨鐢ㄦ埛鍒癛edis鐨剆et闆嗗悎  zadd key value score
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 4.濡傛灉宸茬偣璧烇紝鍙栨秷鐐硅禐
            // 4.1.鏁版嵁搴撶偣璧炴暟 -1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 4.2.鎶婄敤鎴蜂粠Redis鐨剆et闆嗗悎绉婚櫎
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        // 1.鏌ヨtop5鐨勭偣璧炵敤鎴?zrange key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2.瑙ｆ瀽鍑哄叾涓殑鐢ㄦ埛id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        // 3.鏍规嵁鐢ㄦ埛id鏌ヨ鐢ㄦ埛 WHERE id IN ( 5 , 1 ) ORDER BY FIELD(id, 5, 1)
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        // 4.杩斿洖
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 1.鑾峰彇鐧诲綍鐢ㄦ埛
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 2.淇濆瓨鎺㈠簵绗旇
        boolean isSuccess = save(blog);
        if(!isSuccess){
            return Result.fail("鏂板绗旇澶辫触!");
        }
        // 3.鏌ヨ绗旇浣滆€呯殑鎵€鏈夌矇涓?select * from tb_follow where follow_user_id = ?
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        // 4.鎺ㄩ€佺瑪璁癷d缁欐墍鏈夌矇涓?
        for (Follow follow : follows) {
            // 4.1.鑾峰彇绮変笣id
            Long userId = follow.getUserId();
            // 4.2.鎺ㄩ€?
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 5.杩斿洖id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1.鑾峰彇褰撳墠鐢ㄦ埛
        Long userId = UserHolder.getUser().getId();
        // 2.鏌ヨ鏀朵欢绠?ZREVRANGEBYSCORE key Max Min LIMIT offset count
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        // 3.闈炵┖鍒ゆ柇
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        // 4.瑙ｆ瀽鏁版嵁锛歜logId銆乵inTime锛堟椂闂存埑锛夈€乷ffset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0; // 2
        int os = 1; // 2
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) { // 5 4 4 2 2
            // 4.1.鑾峰彇id
            ids.add(Long.valueOf(tuple.getValue()));
            // 4.2.鑾峰彇鍒嗘暟(鏃堕棿鎴筹級
            long time = tuple.getScore().longValue();
            if(time == minTime){
                os++;
            }else{
                minTime = time;
                os = 1;
            }
        }

        // 5.鏍规嵁id鏌ヨblog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        for (Blog blog : blogs) {
            // 5.1.鏌ヨblog鏈夊叧鐨勭敤鎴?
            queryBlogUser(blog);
            // 5.2.鏌ヨblog鏄惁琚偣璧?
            isBlogLiked(blog);
        }

        // 6.灏佽骞惰繑鍥?
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);

        return Result.ok(r);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}


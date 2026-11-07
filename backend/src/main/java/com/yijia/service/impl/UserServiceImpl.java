package com.yijia.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yijia.dto.LoginFormDTO;
import com.yijia.dto.Result;
import com.yijia.dto.UserDTO;
import com.yijia.entity.User;
import com.yijia.mapper.UserMapper;
import com.yijia.service.IUserService;
import com.yijia.utils.RegexUtils;
import com.yijia.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.yijia.utils.RedisConstants.*;
import static com.yijia.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

/**
 * 发送短信验证码方法
 * @param phone 接收验证码的手机号码
 * @param session HTTP会话对象
 * @return 返回操作结果，包含成功或失败信息
 */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号是否合法
        if(RegexUtils.isPhoneInvalid(phone)){
            //手机号不合法，返回错误信息
            return Result.fail("手机号格式错误");
        }
        //频控：同一手机号 60 秒内只能发一次，防止被刷短信（SETNX 过期即自动释放）
        Boolean allowSend = stringRedisTemplate.opsForValue()
                .setIfAbsent(LOGIN_CODE_LIMIT_KEY + phone, "1", LOGIN_CODE_LIMIT_TTL, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(allowSend)) {
            return Result.fail("验证码发送太频繁，请 1 分钟后再试");
        }
        //生成验证码
        String code = RandomUtil.randomString(6);

        //保存验证码到redis，设置过期时间为LOGIN_CODE_TTL分钟
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码，这里只是打印日志，实际项目中应该调用短信服务接口
        //用 info 级别而不是 debug：debug 依赖 logging.level.com.yijia=debug 才会输出，
        //一旦日志级别被调成 info（或换环境），验证码就看不到了
        log.info("发送短信验证码成功，手机号：{}，验证码：{}", phone, code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            //手机号不合法，返回错误信息
            return Result.fail("手机号格式错误");
        }
        //校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String formCode = loginForm.getCode();
        if(cacheCode == null || !cacheCode.equals(formCode)){
            //验证码不一致，返回错误信息
            return Result.fail("验证码错误");
        }
        //验证码一次性使用，校验通过后立即删除，避免被重复使用
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);
        //根据手机号查询用户
        User user = query().eq("phone", phone).one();
        //判断用户是否存在
        if(user == null){
            //用户不存在，注册新用户
            user = new User();
            user.setPhone(phone);
            user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
            save(user);
        }
        //随机生成token
        String token = UUID.randomUUID().toString(true);
        //将用户信息保存到redis，设置过期时间为LOGIN_USER_TTL分钟
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //StringRedisTemplate的Hash值只接受String，必须把Long等类型统一转成字符串，并忽略null字段
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue == null ? null : fieldValue.toString()));
        //存储
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        //设置过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.SECONDS);

        //返回token
        return Result.ok(token);
    }

    /**
     * 签到 key 的月份后缀，形如 :202611
     */
    private static final DateTimeFormatter SIGN_MONTH_FORMAT = DateTimeFormatter.ofPattern(":yyyyMM");

    @Override
    public Result sign() {
        //1.拿到当前登录用户
        Long userId = UserHolder.getUser().getId();
        //2.用当前年月拼出这个月的 bitmap，一个用户一个月一个 key
        LocalDateTime now = LocalDateTime.now();
        String key = USER_SIGN_KEY + userId + now.format(SIGN_MONTH_FORMAT);
        //3.今天是几号，就写第几个 bit（bit 下标从 0 开始，1 号对应 offset 0）
        int dayOfMonth = now.getDayOfMonth();
        Boolean oldValue = stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        //4.SETBIT 本身是幂等的，重复签到不会报错，只是返回原来的值
        if (Boolean.TRUE.equals(oldValue)) {
            log.debug("用户 {} 今天已经签到过了，月份 {}，日期 {}", userId, key, dayOfMonth);
        }
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //1.拿到当前登录用户
        Long userId = UserHolder.getUser().getId();
        //2.拼出本月 key
        LocalDateTime now = LocalDateTime.now();
        String key = USER_SIGN_KEY + userId + now.format(SIGN_MONTH_FORMAT);
        int dayOfMonth = now.getDayOfMonth();
        //3.把本月 1 号到今天的所有 bit 取出来，当成一个无符号整数
        //  等价于 BITFIELD key GET u<dayOfMonth> 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0)
        );
        //4.没有签到记录：key 不存在时返回的是 [0]
        if (result == null || result.isEmpty() || result.get(0) == null || result.get(0) == 0) {
            return Result.ok(0);
        }
        //5.从最低位（今天）往前数连续的 1，遇到 0 就说明断了
        long bits = result.get(0);
        int count = 0;
        while (bits != 0) {
            if ((bits & 1) == 0) {
                break;
            }
            count++;
            bits >>>= 1;
        }
        return Result.ok(count);
    }
}

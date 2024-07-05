package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

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
    @Override
    public Result sendCode(String phone, HttpSession session){
        if(!RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式不正确");
        }
        String code = RandomUtil.randomString(6);

         //session.setAttribute("code",code);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone, code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.debug("手机号：{}，验证码：{}",phone,code);
        return Result.ok();
    }
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session){
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        if(!RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式不正确");
        }
        //Object cachaCode = session.getAttribute("code");
        String cachaCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        log.info("cachaCode:{}",cachaCode);
        if(cachaCode == null || !cachaCode.equals(code)){
            return Result.fail("验证码错误");
        }
        User user = query().eq("phone", phone).one();
        if(user == null){
            createUserWithPhone(phone);
        }

       // session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        String token = UUID.randomUUID().toString();
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String,Object> usermap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()));

        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, usermap);
        log.info("用户登录成功，token:{}",LOGIN_USER_KEY+token);

        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    private void createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
    }

}

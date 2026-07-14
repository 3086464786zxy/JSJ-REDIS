package com.itmk.web.sys_user.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.itmk.config.security.dto.AuthSessionDto;
import com.itmk.config.security.filter.CheckTokenFilter;
import com.itmk.config.security.service.AuthRedisService;
import com.itmk.config.security.service.PermissionCacheService;
import com.itmk.config.security.service.RateLimitService;
import com.itmk.jwt.JwtUtils;
import com.itmk.utils.ResultUtils;
import com.itmk.utils.ResultVo;
import com.itmk.web.sys_menu.entity.SysMenu;
import com.itmk.web.sys_menu.service.SysMenuService;
import com.itmk.web.sys_user.entity.*;
import com.itmk.web.sys_user_role.entity.SysUserRole;
import com.itmk.web.sys_user_role.service.SysUserRoleService;
import com.itmk.web.sys_user.service.SysUserService;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import java.io.ByteArrayOutputStream;
import java.util.stream.Collectors;

@RequestMapping("/api/sysUser")
@RestController
public class SysUserController {
    @Autowired
    private SysUserService sysUserService;
    @Autowired
    private SysUserRoleService sysUserRoleService;
    @Autowired
    private DefaultKaptcha defaultKaptcha;
    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private SysMenuService sysMenuService;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuthRedisService authRedisService;
    @Autowired
    private PermissionCacheService permissionCacheService;
    @Autowired
    private RateLimitService rateLimitService;

    //新增
    @PreAuthorize("hasAuthority('sys:user:add')")
    @PostMapping
    public ResultVo add(@RequestBody SysUser sysUser) {
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(SysUser::getUsername, sysUser.getUsername());
        SysUser res = sysUserService.getOne(queryWrapper);
        if (res != null) {
            return ResultUtils.error("该账户已存在");
        }
        sysUser.setIsAdmin("0");
        //密码加密
        sysUser.setPassword(passwordEncoder.encode(sysUser.getPassword()));
        sysUser.setCreateTime(LocalDateTime.now());
        sysUserService.saveUser(sysUser);
        return ResultUtils.success("新增成功!");
    }

    //编辑
    @PreAuthorize("hasAuthority('sys:user:edit')")
    @PutMapping
    public ResultVo edit(@RequestBody SysUser sysUser) {
        sysUser.setUpdateTime(LocalDateTime.now());
        sysUserService.editUser(sysUser);
        permissionCacheService.invalidateUser(sysUser.getUserId());
        return ResultUtils.success("编辑成功!");
    }

    //删除
    @PreAuthorize("hasAuthority('sys:user:delete')")
    @DeleteMapping("/{userId}")
    public ResultVo delete(@PathVariable("userId") Long userId) {
        sysUserService.deleteUser(userId);
        permissionCacheService.invalidateUser(userId);
        authRedisService.deleteAllSessions(userId);
        return ResultUtils.success("删除成功");
    }

    //列表
    @GetMapping("/list")
    public ResultVo list(SysUserPage parm) {
        //构造分页对象
        IPage<SysUser> page = new Page<>(parm.getCurrentPage(), parm.getPageSize());
        //构造查询对象
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        if (StringUtils.isNotEmpty(parm.getNickname())) {
            queryWrapper.lambda().like(SysUser::getNickName, parm.getNickname());
        }
        if(StringUtils.isNotEmpty(parm.getPhone())) {
            queryWrapper.lambda().like(SysUser::getPhone, parm.getPhone());
        }
        queryWrapper.lambda().orderByDesc(SysUser::getCreateTime);
        //列表查询
        IPage<SysUser> list = sysUserService.page(page, queryWrapper);
        return ResultUtils.success("查询成功",list);
    }

    //根据用户id查询用户的角色
    @GetMapping("/getRoleList")
    public ResultVo getRoleList(Long userId) {
        QueryWrapper<SysUserRole> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(SysUserRole::getUserId, userId);
        List<SysUserRole> list = sysUserRoleService.list(queryWrapper);
        List<Long> roleList = new ArrayList<>();
        Optional.ofNullable(list).orElse(new ArrayList<>())
                .forEach(item -> {
                    roleList.add(item.getRoleId());
                });
        return ResultUtils.success("查询成功!",roleList);
    }

    //重置密码
    @PreAuthorize("hasAuthority('sys:user:reset')")
    @PostMapping("/resetPassword")
    public ResultVo resetPassword(@RequestBody SysUser sysUser) {
        UpdateWrapper<SysUser> updateWrapper = new UpdateWrapper<>();
        updateWrapper.lambda().eq(SysUser::getUserId, sysUser.getUserId())
                .set(SysUser::getPassword, passwordEncoder.encode("666666"));
        if(sysUserService.update(updateWrapper)) {
            authRedisService.deleteAllSessions(sysUser.getUserId());
            return ResultUtils.success("密码重置成功!");
        }
        return ResultUtils.error("密码重置失败!");
    }

    //图片验证码
    @PostMapping("/getImage")
    public ResultVo imageCode(HttpServletRequest request) {
        if (!rateLimitService.allowCaptcha(request)) {
            return ResultUtils.error("验证码获取过于频繁，请稍后再试", 429);
        }

        String text = defaultKaptcha.createText();
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        // Redis 自动过期，登录校验时会原子读取并删除。
        authRedisService.saveCaptcha(captchaId, text);
        BufferedImage bufferedImage = defaultKaptcha.createImage(text);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(bufferedImage, "jpg", outputStream);
            byte[] bytes = outputStream.toByteArray();
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String captchaBase64 = "data:image/jpeg;base64," + base64;
            return ResultUtils.success("生成成功", new CaptchaVo(captchaId, captchaBase64));
        } catch (IOException e) {
            return ResultUtils.error("验证码生成失败");
        }
    }

    //登录
    @PostMapping("/login")
    public ResultVo login(HttpServletRequest request,@RequestBody LoginParm parm) {
        if (!rateLimitService.allowLogin(request, parm.getUsername())) {
            return ResultUtils.error("登录尝试过于频繁，请稍后再试", 429);
        }

        if (StringUtils.isEmpty(parm.getCaptchaId())) {
            return ResultUtils.error("验证码过期!");
        }
        String expectedCode = authRedisService.consumeCaptcha(parm.getCaptchaId());
        if (StringUtils.isEmpty(expectedCode)) {
            return ResultUtils.error("验证码过期!");
        }
        if (!expectedCode.equalsIgnoreCase(parm.getCode())) {
            return ResultUtils.error("验证码不正确!");
        }

        // 用户名、密码由 Spring Security 和 BCrypt 完成认证。
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(parm.getUsername(), parm.getPassword());
        Authentication authentication = authenticationManager.authenticate(authenticationToken);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        SysUser user = (SysUser)authentication.getPrincipal();

        String sessionId = UUID.randomUUID().toString();
        Duration sessionTtl = Duration.ofMinutes(jwtUtils.getExpiration());
        authRedisService.createSession(
                sessionId,
                new AuthSessionDto(user.getUserId(), user.getUsername(), System.currentTimeMillis()),
                sessionTtl
        );
        permissionCacheService.cacheLoginUser(user);

        LoginVo vo = new LoginVo();
        vo.setUserId(user.getUserId());
        vo.setNickName(user.getNickName());
        Map<String,String> mp = new HashMap<>();
        mp.put("userId",Long.toString(user.getUserId()));
        mp.put("username",user.getUsername());
        mp.put("sid", sessionId);
        String token = jwtUtils.generateToken(mp);
        vo.setToken(token);
        return ResultUtils.success("登录成功",vo);
    }

    //查询菜单树
    @PreAuthorize("hasAuthority('sys:role:assign')")
    @GetMapping("/getAssingTree")
    public ResultVo getAssingTree(AssignTreeParm assignTreeParm) {
        System.out.println("getSSINGtREE");
        AssignTreeVo assignTreeVo = sysUserService.getAssignTreeVo(assignTreeParm);
        return ResultUtils.success("查询成功",assignTreeVo);
    }

    @PostMapping("/updatePassword")
    public ResultVo updatePassword(@RequestBody UpdatePasswordParm parm) {
        SysUser sysUser = sysUserService.getById(parm.getUserId());
        //if (!sysUser.getPassword().equals(parm.getOldPassword())) {
        //    return ResultUtils.error("原密码不正确");
        //}
        if (!passwordEncoder.matches(parm.getOldPassword(), sysUser.getPassword())) {
            return ResultUtils.error("原密码不正确!");
        }
        //更新条件
        UpdateWrapper<SysUser> updateWrapper = new UpdateWrapper<>();
        updateWrapper.lambda().set(SysUser::getPassword,passwordEncoder.encode(parm.getPassword()))
                .eq(SysUser::getUserId, parm.getUserId());
        if (sysUserService.update(updateWrapper)) {
            authRedisService.deleteAllSessions(parm.getUserId());
            return ResultUtils.success("密码修改成功!");
        }
        return ResultUtils.error("密码修改失败!");
    }

    //获取用户的信息
    @GetMapping("/getUserInfo")
    public ResultVo getUserInfo(Long userId) {
        System.out.println("getUserInfo");
        //根据id查询用户信息
        SysUser user = sysUserService.getById(userId);
        List<SysMenu> menuList = null;
        //判断是否是超级管理员
        if (StringUtils.isNotEmpty(user.getIsAdmin()) && "1".equals(user.getIsAdmin())) {
            //超级管理员,直接查询得到所有菜单
            menuList = sysMenuService.list();
        } else {
            menuList = sysMenuService.getMenuByUserId(userId);
        }
        //获取菜单表的code字段
        List<String> collect = Optional.ofNullable(menuList).orElse(new ArrayList<>())
                .stream()
                .filter(Objects::nonNull)
                .filter(item -> StringUtils.isNotEmpty(item.getCode()))
                .map(item -> item.getCode())
                .collect(Collectors.toList());
        //设置返回值
        UserInfo userInfo = new UserInfo();
        userInfo.setName(user.getNickName());
        userInfo.setUserId(user.getUserId());
        userInfo.setPermissions(collect.toArray());
        return ResultUtils.success("查询成功",userInfo);
    }

    //退出登录
    @PostMapping("/loginOut")
    public ResultVo loginOut(HttpServletRequest request, HttpServletResponse response) {
        String token = CheckTokenFilter.resolveToken(request);
        if (token != null) {
            try {
                DecodedJWT jwt = jwtUtils.jwtDecode(token);
                Long userId = Long.valueOf(jwt.getClaim("userId").asString());
                String sessionId = jwt.getClaim("sid").asString();
                authRedisService.deleteSession(userId, sessionId);
            } catch (RuntimeException ignored) {
                // Token 已无效时也允许客户端完成本地退出。
            }
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }
        return ResultUtils.success("退出成功!");
    }
}

package com.itmk.web.sys_user.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.itmk.config.security.dto.AuthSessionDto;
import com.itmk.config.security.dto.RefreshTokenDto;
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
import com.itmk.web.sys_user.service.SysUserService;
import com.itmk.web.sys_user_role.entity.SysUserRole;
import com.itmk.web.sys_user_role.service.SysUserRoleService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

@RequestMapping("/api/sysUser")
@RestController
public class SysUserController {
    @Autowired private SysUserService sysUserService;
    @Autowired private SysUserRoleService sysUserRoleService;
    @Autowired private DefaultKaptcha defaultKaptcha;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private SysMenuService sysMenuService;
    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuthRedisService authRedisService;
    @Autowired private PermissionCacheService permissionCacheService;
    @Autowired private RateLimitService rateLimitService;

    @Value("${app.security.refresh-token.cookie-name:refresh_token}")
    private String cookieName;

    @Value("${app.security.refresh-token.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${app.security.refresh-token.cookie-same-site:Lax}")
    private String cookieSameSite;

    @Autowired private com.itmk.config.security.service.ManagementPolicy managementPolicy;
    @Autowired private com.itmk.config.security.service.SecurityStateService securityStates;

    // 新增
    @PreAuthorize("hasAuthority('sys:user:add')")
    @PostMapping
    @com.itmk.config.audit.AuditedChange
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public ResultVo add(@jakarta.validation.Valid @RequestBody UserWriteParm parm) {
        if (parm.getUserId() != null) throw new IllegalArgumentException("新增用户不能指定ID");
        com.itmk.config.security.service.PasswordPolicy.validate(parm.getPassword());
        SysUser sysUser = parm.toUser();
        sysUser.setPassword(parm.getPassword());
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(SysUser::getUsername, sysUser.getUsername());
        SysUser res = sysUserService.getOne(queryWrapper);
        if (res != null) {
            return ResultUtils.error("该账户已存在");
        }
        sysUser.setIsAdmin("0");
        // 密码加密
        sysUser.setPassword(passwordEncoder.encode(sysUser.getPassword()));
        sysUser.setCreateTime(LocalDateTime.now());
        sysUserService.saveUser(sysUser);
        parm.setUserId(sysUser.getUserId());
        return ResultUtils.success("新增成功!");
    }

    // 编辑
    @PreAuthorize("hasAuthority('sys:user:edit')")
    @PutMapping
    @com.itmk.config.audit.AuditedChange
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public ResultVo edit(@jakarta.validation.Valid @RequestBody UserWriteParm parm) {
        if (parm.getUserId() == null) throw new IllegalArgumentException("用户ID不能为空");
        if (parm.getPassword() != null && !parm.getPassword().isEmpty())
            throw new IllegalArgumentException("编辑用户请通过密码修改接口修改密码");
        managementPolicy.assertUser(parm.getUserId(), false);
        SysUser sysUser = parm.toUser();
        sysUser.setUpdateTime(LocalDateTime.now());
        sysUserService.editUser(sysUser);
        return ResultUtils.success("编辑成功!");
    }

    // 删除
    @PreAuthorize("hasAuthority('sys:user:delete')")
    @DeleteMapping("/{userId}")
    @com.itmk.config.audit.AuditedChange
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public ResultVo delete(@PathVariable("userId") Long userId) {
        managementPolicy.assertUser(userId, true);
        sysUserService.deleteUser(userId);
        return ResultUtils.success("删除成功");
    }

    // 列表
    @PreAuthorize(
            "hasAnyAuthority('sys:user','sys:user:list','sys:user:add','sys:user:edit','sys:user:delete','sys:user:reset')")
    @GetMapping("/list")
    public ResultVo list(@jakarta.validation.Valid SysUserPage parm) {
        // 构造分页对象
        IPage<SysUser> page = new Page<>(parm.getCurrentPage(), parm.getPageSize());
        // 构造查询对象
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        if (StringUtils.isNotEmpty(parm.getNickname())) {
            queryWrapper.lambda().like(SysUser::getNickName, parm.getNickname());
        }
        if (StringUtils.isNotEmpty(parm.getPhone())) {
            queryWrapper.lambda().like(SysUser::getPhone, parm.getPhone());
        }
        queryWrapper.select(SysUser.class, field -> !"password".equals(field.getProperty()));
        queryWrapper.lambda().orderByDesc(SysUser::getCreateTime);
        // 列表查询
        IPage<SysUser> list = sysUserService.page(page, queryWrapper);
        return ResultUtils.success("查询成功", list);
    }

    // 根据用户id查询用户的角色
    @PreAuthorize("hasAnyAuthority('sys:user:add','sys:user:edit')")
    @GetMapping("/getRoleList")
    public ResultVo getRoleList(Long userId) {
        QueryWrapper<SysUserRole> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(SysUserRole::getUserId, userId);
        List<SysUserRole> list = sysUserRoleService.list(queryWrapper);
        List<Long> roleList = new ArrayList<>();
        Optional.ofNullable(list)
                .orElse(new ArrayList<>())
                .forEach(
                        item -> {
                            roleList.add(item.getRoleId());
                        });
        return ResultUtils.success("查询成功!", roleList);
    }

    // 重置密码
    @PreAuthorize("hasAuthority('sys:user:reset')")
    @PostMapping("/resetPassword")
    @com.itmk.config.audit.AuditedChange
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public ResultVo resetPassword(
            @jakarta.validation.Valid @RequestBody ResetPasswordParm sysUser) {
        managementPolicy.assertUser(sysUser.getUserId(), false);
        com.itmk.config.security.service.PasswordPolicy.validate(sysUser.getPassword());
        UpdateWrapper<SysUser> updateWrapper = new UpdateWrapper<>();
        updateWrapper
                .lambda()
                .eq(SysUser::getUserId, sysUser.getUserId())
                .set(SysUser::getPassword, passwordEncoder.encode(sysUser.getPassword()));
        if (sysUserService.update(updateWrapper)) {
            securityStates.revokeUser(sysUser.getUserId());
            return ResultUtils.success("密码重置成功!");
        }
        return ResultUtils.error("密码重置失败!");
    }

    // 图片验证码
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

    // 登录
    @PostMapping("/login")
    public ResultVo login(
            HttpServletRequest request, HttpServletResponse response, @RequestBody LoginParm parm) {
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
        SysUser user = (SysUser) authentication.getPrincipal();

        String sessionId = UUID.randomUUID().toString();
        Duration sessionTtl = authRedisService.getSessionIdleTimeout();
        authRedisService.createSession(
                sessionId,
                new AuthSessionDto(
                        user.getUserId(), user.getUsername(), System.currentTimeMillis(), user.getSessionVersion()),
                sessionTtl);
        permissionCacheService.cacheLoginUser(user);
        request.setAttribute("audit.userId", user.getUserId());
        if (!securityStates.validSession(user.getUserId(), user.getUsername(), sessionId, user.getSessionVersion()))
            throw new com.itmk.config.security.exception.CustomerAuthenionException("账户信息已变更，请重新登录");

        LoginVo vo = new LoginVo();
        vo.setUserId(user.getUserId());
        vo.setNickName(user.getNickName());
        Map<String, String> mp = new HashMap<>();
        mp.put("userId", Long.toString(user.getUserId()));
        mp.put("username", user.getUsername());
        mp.put("sid", sessionId);
        String token = jwtUtils.generateToken(mp);
        vo.setToken(token);
        vo.setIdleTimeoutSeconds(sessionTtl.toSeconds());

        // 签发 RefreshToken 并写入 httpOnly Cookie
        String refreshToken = authRedisService.newRefreshToken(user.getUserId());
        Duration refreshTtl = Duration.ofMinutes(jwtUtils.getRefreshExpiration());
        RefreshTokenDto refreshDto =
                new RefreshTokenDto(user.getUserId(), user.getUsername(), sessionId);
        authRedisService.saveRefreshToken(refreshToken, refreshDto, refreshTtl);
        setRefreshCookie(response, refreshToken, refreshTtl);

        return ResultUtils.success("登录成功", vo);
    }

    // 查询菜单树
    @PreAuthorize("hasAuthority('sys:role:assign')")
    @GetMapping("/getAssingTree")
    public ResultVo getAssingTree(AssignTreeParm assignTreeParm) {
        assignTreeParm.setUserId(managementPolicy.actor().getUserId());
        managementPolicy.assertRole(assignTreeParm.getRoleId());
        AssignTreeVo assignTreeVo = sysUserService.getAssignTreeVo(assignTreeParm);
        return ResultUtils.success("查询成功", assignTreeVo);
    }

    @PostMapping("/updatePassword")
    @com.itmk.config.audit.AuditedChange
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public ResultVo updatePassword(
            HttpServletRequest request, @RequestBody UpdatePasswordParm parm) {
        com.itmk.config.security.service.PasswordPolicy.validate(parm.getPassword());
        if (parm.getOldPassword() == null) throw new IllegalArgumentException("原密码不能为空");
        Long currentUserId = extractUserId(request);
        SysUser sysUser = sysUserService.getById(currentUserId);
        // if (!sysUser.getPassword().equals(parm.getOldPassword())) {
        //    return ResultUtils.error("原密码不正确");
        // }
        if (!passwordEncoder.matches(parm.getOldPassword(), sysUser.getPassword())) {
            return ResultUtils.error("原密码不正确!");
        }
        // 更新条件
        UpdateWrapper<SysUser> updateWrapper = new UpdateWrapper<>();
        updateWrapper
                .lambda()
                .set(SysUser::getPassword, passwordEncoder.encode(parm.getPassword()))
                .eq(SysUser::getPassword, sysUser.getPassword())
                .eq(SysUser::getUserId, currentUserId);
        if (sysUserService.update(updateWrapper)) {
            securityStates.revokeUser(currentUserId);
            return ResultUtils.success("密码修改成功!");
        }
        return ResultUtils.error("密码修改失败!");
    }

    // 获取当前用户的信息
    @GetMapping("/getUserInfo")
    public ResultVo getUserInfo(HttpServletRequest request) {
        Long currentUserId = extractUserId(request);
        // 根据id查询用户信息
        SysUser user = sysUserService.getById(currentUserId);
        List<SysMenu> menuList = null;
        // 判断是否是超级管理员
        if (StringUtils.isNotEmpty(user.getIsAdmin()) && "1".equals(user.getIsAdmin())) {
            // 超级管理员,直接查询得到所有菜单
            menuList = sysMenuService.list();
        } else {
            menuList = sysMenuService.getMenuByUserId(currentUserId);
        }
        // 获取菜单表的code字段
        List<String> collect =
                Optional.ofNullable(menuList).orElse(new ArrayList<>()).stream()
                        .filter(Objects::nonNull)
                        .filter(item -> StringUtils.isNotEmpty(item.getCode()))
                        .flatMap(item -> java.util.Arrays.stream(item.getCode().split(",")))
                        .map(String::trim)
                        .filter(code -> !code.isEmpty())
                        .distinct()
                        .collect(Collectors.toList());
        // 设置返回值
        UserInfo userInfo = new UserInfo();
        userInfo.setName(user.getNickName());
        userInfo.setUserId(user.getUserId());
        userInfo.setPermissions(collect.toArray());
        return ResultUtils.success("查询成功", userInfo);
    }

    // 退出登录
    @PostMapping("/loginOut")
    public ResultVo loginOut(HttpServletRequest request, HttpServletResponse response) {
        // 1. 尝试从 JWT 中解析并删除 Redis 会话
        String token = CheckTokenFilter.resolveToken(request);
        if (token != null) {
            try {
                DecodedJWT jwt = jwtUtils.jwtDecode(token);
                Long userId = Long.valueOf(jwt.getClaim("userId").asString());
                String sessionId = jwt.getClaim("sid").asString();
                durableLogout(request, userId, sessionId);
            } catch (IllegalArgumentException ignored) {
                // Token 已无效时也允许客户端完成本地退出。
            }
        }

        // 2. 删除 RefreshToken 并清除 Cookie
        String refreshToken = extractCookie(request);
        if (refreshToken != null) {
            RefreshTokenDto stored = authRedisService.getRefreshToken(refreshToken);
            if (stored != null) {
                durableLogout(request, stored.getUserId(), stored.getSessionId());
                try { authRedisService.deleteRefreshToken(refreshToken, stored.getUserId()); }
                catch (org.springframework.dao.DataAccessException e) {
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("Logout Redis cleanup failed; database revocation retained");
                }
            }
        }
        clearRefreshCookie(response);

        // 3. 清除 SecurityContext
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }
        return ResultUtils.success("退出成功!");
    }

    /** 从请求的 JWT Token 中提取当前用户 ID */
    private void durableLogout(HttpServletRequest request, Long userId, String sessionId) {
        securityStates.revokeSession(userId, sessionId);
        request.setAttribute("audit.userId", userId);
        try { authRedisService.deleteSession(userId, sessionId); }
        catch (org.springframework.dao.DataAccessException e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Logout Redis cleanup failed; database revocation retained");
        }
    }

    private Long extractUserId(HttpServletRequest request) {
        String token = CheckTokenFilter.resolveToken(request);
        DecodedJWT jwt = jwtUtils.jwtDecode(token);
        return Long.valueOf(jwt.getClaim("userId").asString());
    }

    // ────────── Cookie 工具方法 ──────────

    private void setRefreshCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie =
                ResponseCookie.from(cookieName, value)
                        .httpOnly(true)
                        .secure(cookieSecure)
                        .sameSite(cookieSameSite)
                        .path("/api/")
                        .maxAge(maxAge)
                        .build();
        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie =
                ResponseCookie.from(cookieName, "")
                        .httpOnly(true)
                        .secure(cookieSecure)
                        .sameSite(cookieSameSite)
                        .path("/api/")
                        .maxAge(0)
                        .build();
        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String extractCookie(HttpServletRequest request) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}

package com.itmk.web.sys_user.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.code.kaptcha.impl.DefaultKaptcha;
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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.awt.image.BufferedImage;
import java.io.IOException;
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
        return ResultUtils.success("编辑成功!");
    }

    //删除
    @PreAuthorize("hasAuthority('sys:user:delete')")
    @DeleteMapping("/{userId}")
    public ResultVo delete(@PathVariable("userId") Long userId) {
        sysUserService.deleteUser(userId);
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
            return ResultUtils.success("密码重置成功!");
        }
        return ResultUtils.error("密码重置失败!");
    }

    //图片验证码
    @PostMapping("/getImage")
    public ResultVo imageCode(HttpServletRequest request) {
        HttpSession session = request.getSession();
        // 生成验证码文本
        String text = defaultKaptcha.createText();
        // 存入 Session
        session.setAttribute("code", text);
        // 生成验证码图片
        BufferedImage bufferedImage = defaultKaptcha.createImage(text);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(bufferedImage, "jpg", outputStream);
            byte[] bytes = outputStream.toByteArray();
            // ✅ 使用 Java 官方 Base64 编码
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String captchaBase64 = "data:image/jpeg;base64," + base64;
            return new ResultVo("生成成功", 200, captchaBase64);
        } catch (IOException e) {
            e.printStackTrace();
            return new ResultVo("生成失败", 500, null);
        }
    }

    //登录
    @PostMapping("/login")
    public ResultVo login(HttpServletRequest request,@RequestBody LoginParm parm) {
        //获取前端传递过来的code(验证码)
        String code = parm.getCode();
        //获取sesson
        HttpSession session = request.getSession();
        //获取session里面的code(验证码)
        String code1 = (String) session.getAttribute("code");
        if (StringUtils.isEmpty(code1)) {
            return ResultUtils.error("验证码过期!");
        }
        //判断前端传递进来的code和session里面的code是否相等
        if (!code1.equals(code)) {
            System.out.println("------------------------------------------");
            return ResultUtils.error("验证码不正确!");
        }

        //
        String password = passwordEncoder.encode(parm.getPassword());
        //查询用户信息, 交给springsecurity查询
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(parm.getUsername(), parm.getPassword());
        Authentication authentication = authenticationManager.authenticate(authenticationToken);
        //交给springsecurity
        SecurityContextHolder.getContext().setAuthentication(authentication);
        //获取用户信息
        SysUser user = (SysUser)authentication.getPrincipal();
        //QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        //queryWrapper.lambda().eq(SysUser::getUsername,parm.getUsername())
        //        .eq(SysUser::getPassword,parm.getPassword());
        //SysUser one = sysUserService.getOne(queryWrapper);
        //if (one == null) {
        //    return ResultUtils.error("用户名或密码不正确");
        //}

        //返回用户信息和token
        LoginVo vo = new LoginVo();
        vo.setUserId(user.getUserId());
        vo.setNickName(user.getNickName());
        //生成token
        Map<String,String> mp = new HashMap<>();
        mp.put("userId",Long.toString(user.getUserId()));
        mp.put("username",user.getUsername());
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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }
        return ResultUtils.success("退出成功!");
    }
}

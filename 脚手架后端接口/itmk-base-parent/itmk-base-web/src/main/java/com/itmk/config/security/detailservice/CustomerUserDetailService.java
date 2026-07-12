package com.itmk.config.security.detailservice;

import com.itmk.config.redis.RedisService;
import com.itmk.config.security.exception.CustomerAuthenionException;
import com.itmk.web.sys_menu.entity.SysMenu;
import com.itmk.web.sys_menu.service.SysMenuService;
import com.itmk.web.sys_user.entity.SysUser;
import com.itmk.web.sys_user.service.SysUserService;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component("customerUserDetailService")
public class CustomerUserDetailService implements UserDetailsService {
    @Autowired
    private SysUserService sysUserService;
    @Autowired
    private SysMenuService sysMenuService;
    @Autowired
    private RedisService redisService;
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserService.loadUser(username);
        if (user == null) {
            throw new CustomerAuthenionException("用户名错误或账户不存在");
        }
        //查询菜单权限
        List<SysMenu> menuList = null;
        //判断是否是超级管理员
        if (StringUtils.isNotEmpty(user.getIsAdmin()) && "1".equals(user.getIsAdmin())) {
            //超级管理员，直接全部查询
            menuList = sysMenuService.list();
        } else {
            menuList = sysMenuService.getMenuByUserId(user.getUserId());
        }
        //获取菜单表的code字段
        List<String> collect = Optional.ofNullable(menuList).orElse(new ArrayList<>())
                .stream()
                .filter(item -> item != null && StringUtils.isNotEmpty(item.getCode()))
                .map(item -> item.getCode())
                .collect(Collectors.toList());
        //把权限字段交给springsecurity进行管理
        String[] strings = collect.toArray(new String[0]);
        List<GrantedAuthority> authorities = AuthorityUtils.createAuthorityList(strings);
        user.setAuthorities(authorities);
        //将用户权限缓存到 Redis，TTL 30 分钟与 JWT 保持一致
        redisService.set("user_permissions:" + username, user, 30, java.util.concurrent.TimeUnit.MINUTES);
        return user;
    }
}

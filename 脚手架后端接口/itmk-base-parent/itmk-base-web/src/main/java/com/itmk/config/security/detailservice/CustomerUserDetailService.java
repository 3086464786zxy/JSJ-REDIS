package com.itmk.config.security.detailservice;

import com.itmk.config.security.dto.PermissionDto;
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
import java.util.Set;
import java.util.stream.Collectors;

@Component("customerUserDetailService")
public class CustomerUserDetailService implements UserDetailsService {
    @Autowired
    private SysUserService sysUserService;
    @Autowired
    private SysMenuService sysMenuService;
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserService.loadUser(username);
        if (user == null) {
            throw new CustomerAuthenionException("用户名错误或账户不存在");
        }
        List<String> collect = loadPermissionCodes(user);
        //把权限字段交给springsecurity进行管理
        String[] strings = collect.toArray(new String[0]);
        List<GrantedAuthority> authorities = AuthorityUtils.createAuthorityList(strings);
        user.setAuthorities(authorities);
        return user;
    }

    /** 缓存未命中时只组装最小权限 DTO，不返回密码等敏感信息。 */
    public PermissionDto loadPermissionByUserId(Long userId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return null;
        }
        return new PermissionDto(
                user.getUserId(),
                user.getUsername(),
                user.isEnabled(),
                Set.copyOf(loadPermissionCodes(user))
        );
    }

    private List<String> loadPermissionCodes(SysUser user) {
        List<SysMenu> menuList;
        if (StringUtils.isNotEmpty(user.getIsAdmin()) && "1".equals(user.getIsAdmin())) {
            menuList = sysMenuService.list();
        } else {
            menuList = sysMenuService.getMenuByUserId(user.getUserId());
        }
        return Optional.ofNullable(menuList).orElseGet(ArrayList::new)
                .stream()
                .filter(item -> item != null && StringUtils.isNotEmpty(item.getCode()))
                .map(SysMenu::getCode)
                .distinct()
                .collect(Collectors.toList());
    }
}

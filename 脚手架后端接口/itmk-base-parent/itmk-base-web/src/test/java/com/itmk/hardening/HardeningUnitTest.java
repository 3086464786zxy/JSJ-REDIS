package com.itmk.hardening;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itmk.config.redis.RedisService;
import com.itmk.config.security.detailservice.CustomerUserDetailService;
import com.itmk.config.security.dto.PermissionDto;
import com.itmk.config.security.service.*;
import com.itmk.jwt.JwtUtils;
import com.itmk.web.sys_menu.entity.*;
import com.itmk.web.sys_user.entity.SysUser;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

class HardeningUnitTest {
    @Test
    void passwordHashIsNeverSerialized() throws Exception {
        SysUser user = new SysUser();
        user.setPassword("secret-hash");
        assertFalse(new ObjectMapper().writeValueAsString(user).contains("secret-hash"));
    }

    @Test
    void rejectsWeakJwtAndOversizeBcryptPasswords() {
        JwtUtils jwt = new JwtUtils();
        jwt.setSecret("weak");
        assertThrows(IllegalStateException.class, jwt::init);
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("666666"));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("密".repeat(25)));
        assertDoesNotThrow(() -> PasswordPolicy.validate("long enough password"));
    }

    @Test
    void cacheInvalidationWinsAgainstAnInFlightOldFill() {
        RedisService redis = mock(RedisService.class);
        CustomerUserDetailService details = mock(CustomerUserDetailService.class);
        SecurityStateService states = mock(SecurityStateService.class);
        PermissionCacheService cache = new PermissionCacheService(redis, details,states,new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        ReflectionTestUtils.setField(cache, "ttl", 900L);
        AtomicReference<String> version = new AtomicReference<>("0");
        when(states.read(2L,null)).thenAnswer(i -> new com.itmk.config.security.dto.SecurityState(2L,"user",0,Long.parseLong(version.get()),0,true,false));
        PermissionDto old = new PermissionDto(2L, "user", true, Set.of("old")),
                fresh = new PermissionDto(2L, "user", true, Set.of());
        when(details.loadPermissionByUserId(2L))
                .thenAnswer(
                        i -> {
                            version.set("1");
                            return old;
                        })
                .thenReturn(fresh);
        assertSame(fresh, cache.getOrLoad(2L, "user"));
        verify(redis).setJson(eq("authz:db-user:{2}:v0:0"), eq(old), any());
        verify(redis).getJson("authz:db-user:{2}:v0:1", PermissionDto.class);
    }

    private SysMenu menu(long id, long parent) {
        SysMenu m = new SysMenu();
        m.setMenuId(id);
        m.setParentId(parent);
        m.setType("1");
        m.setTitle("Menu");
        return m;
    }

    @Test
    void wideMenuTreeSupportsNullCodesDuplicatesAndUnreachableCycles() {
        List<SysMenu> source = new ArrayList<>();
        source.add(menu(1, 0));
        for (long id = 2; id <= 20000; id++) source.add(menu(id, 1));
        source.add(menu(2, 1));
        source.add(null);
        source.add(menu(20001, 20002));
        source.add(menu(20002, 20001));
        var tree = MakeMenuTree.makeTree(source, 0L);
        assertEquals(1, tree.size());
        assertEquals(19999, tree.getFirst().getChildren().size());
        assertEquals(1, MakeMenuTree.makeRouter(source, 0L).size());
        assertTrue(source.getFirst().getChildren().isEmpty());
    }

    @Test
    void excessivelyDeepLegacyTreeFailsWithoutStackOverflow() {
        List<SysMenu> source = new ArrayList<>();
        for (long id = 1; id <= 10000; id++) source.add(menu(id, id - 1));
        assertThrows(IllegalArgumentException.class, () -> MakeMenuTree.makeTree(source, 0L));
    }
}

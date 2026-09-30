package com.itmk.hardening;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.itmk.config.redis.RedisService;
import com.itmk.config.security.dto.*;
import com.itmk.config.security.service.*;
import com.itmk.jwt.JwtUtils;
import com.itmk.web.sys_role_menu.entity.SaveMenuParm;
import com.itmk.web.sys_role_menu.mapper.RoleMenuMapper;
import com.itmk.web.sys_role_menu.service.RoleMenuService;
import com.itmk.web.sys_user.service.SysUserService;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:hardening;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "jwt.secret=hardening-tests-secret-at-least-32-bytes",
            "logging.file.name=target/hardening-test.log"
        })
@AutoConfigureMockMvc
@Sql("/hardening-schema.sql")
class HardeningIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtUtils jwt;
    @Autowired JdbcTemplate db;
    @Autowired RoleMenuService roleMenus;
    @Autowired SysUserService users;
    @MockitoBean RedisService redis;
    @MockitoBean AuthRedisService sessions;
    @MockitoBean PermissionCacheService permissions;
    @MockitoSpyBean RoleMenuMapper menuMapper;

    private String token(String username, long id, String... codes) {
        when(sessions.getSession(id, "test-session"))
                .thenReturn(new AuthSessionDto(id, username, 0L));
        when(permissions.getOrLoad(id, username))
                .thenReturn(new PermissionDto(id, username, true, Set.of(codes)));
        return "Bearer "
                + jwt.generateToken(
                        Map.of(
                                "userId",
                                Long.toString(id),
                                "username",
                                username,
                                "sid",
                                "test-session"));
    }

    private void actor(String username) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void userInfoNormalizesCommaSeparatedPermissions() throws Exception {
        mvc.perform(get("/api/sysUser/getUserInfo").header("Authorization", token("target", 3)))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.data.permissions",
                                org.hamcrest.Matchers.hasItems("sys:user:edit", "sys:user:reset")))
                .andExpect(
                        jsonPath(
                                "$.data.permissions",
                                org.hamcrest.Matchers.not(
                                        org.hamcrest.Matchers.hasItem(
                                                "sys:user:edit,sys:user:reset"))));
    }

    @Test
    void authenticatedUserCannotAssignRoleWithoutPermission() throws Exception {
        mvc.perform(
                        post("/api/role/saveRoleMenu")
                                .header("Authorization", token("ordinary", 2))
                                .header("X-Requested-With", "XMLHttpRequest")
                                .contentType("application/json")
                                .content("{\"roleId\":20,\"list\":[100]}"))
                .andExpect(status().isForbidden());
        assertEquals(
                1,
                db.queryForObject(
                        "SELECT COUNT(*) FROM sys_role_menu WHERE role_id=20", Integer.class));
    }

    @Test
    void permittedUserCannotGrantOutsideTheirScope() throws Exception {
        mvc.perform(
                        post("/api/role/saveRoleMenu")
                                .header("Authorization", token("ordinary", 2, "sys:role:assign"))
                                .header("X-Requested-With", "XMLHttpRequest")
                                .contentType("application/json")
                                .content("{\"roleId\":20,\"list\":[200]}"))
                .andExpect(status().isForbidden());
        assertEquals(
                100L,
                db.queryForObject(
                        "SELECT menu_id FROM sys_role_menu WHERE role_id=20", Long.class));
    }

    @Test
    void unsafeCookieRequestsAndCrossSiteRequestsAreRejected() throws Exception {
        mvc.perform(post("/api/refresh")).andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/refresh")
                                .header("X-Requested-With", "XMLHttpRequest")
                                .header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/refresh")
                                .header("X-Requested-With", "XMLHttpRequest")
                                .header("Sec-Fetch-Site", "same-site"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listDoesNotExposePasswordAndUnboundedPageIsRejected() throws Exception {
        String auth = token("admin", 1, "sys:user:list");
        mvc.perform(get("/api/sysUser/list").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].password").doesNotExist());
        mvc.perform(
                        get("/api/sysUser/list")
                                .param("pageSize", "10000")
                                .header("Authorization", auth))
                .andExpect(status().isBadRequest());
    }

    @Test
    void extraAdminFieldsAreRejectedAndNormalEditPreservesFlagsAndPassword() throws Exception {
        db.update(
                "UPDATE sys_user SET is_account_non_locked=FALSE, is_enabled=FALSE WHERE"
                        + " user_id=3");
        String auth = token("admin", 1, "sys:user:edit");
        String body =
                "{\"userId\":3,\"username\":\"target\",\"nickName\":\"Changed\",\"roleId\":\"10\"}";
        mvc.perform(
                        put("/api/sysUser")
                                .header("Authorization", auth)
                                .header("X-Requested-With", "XMLHttpRequest")
                                .contentType("application/json")
                                .content(
                                        body.substring(0, body.length() - 1)
                                                + ",\"isAdmin\":\"1\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        put("/api/sysUser")
                                .header("Authorization", auth)
                                .header("X-Requested-With", "XMLHttpRequest")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isOk());
        assertFalse(
                db.queryForObject(
                        "SELECT is_enabled FROM sys_user WHERE user_id=3", Boolean.class));
        assertFalse(
                db.queryForObject(
                        "SELECT is_account_non_locked FROM sys_user WHERE user_id=3",
                        Boolean.class));
        assertEquals(
                "0",
                db.queryForObject("SELECT is_admin FROM sys_user WHERE user_id=3", String.class));
        assertEquals(
                "$2a$10$example",
                db.queryForObject("SELECT password FROM sys_user WHERE user_id=3", String.class));
    }

    @Test
    void delegatedPasswordResetCannotTakeOverMorePrivilegedAccount() throws Exception {
        mvc.perform(
                        post("/api/sysUser/resetPassword")
                                .header("Authorization", token("ordinary", 2, "sys:user:reset"))
                                .header("X-Requested-With", "XMLHttpRequest")
                                .contentType("application/json")
                                .content("{\"userId\":3,\"password\":\"very-long-new-password\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void emptyMenuSelectionClearsPermissions() {
        actor("admin");
        SaveMenuParm parm = new SaveMenuParm();
        parm.setRoleId(20L);
        parm.setList(List.of());
        roleMenus.saveRoleMenu(parm);
        assertEquals(
                0,
                db.queryForObject(
                        "SELECT COUNT(*) FROM sys_role_menu WHERE role_id=20", Integer.class));
    }

    @Test
    void insertFailureRollsBackPreviouslyDeletedRoleMenus() {
        actor("admin");
        SaveMenuParm parm = new SaveMenuParm();
        parm.setRoleId(20L);
        parm.setList(List.of(200L));
        doThrow(
                        new org.springframework.dao.DataIntegrityViolationException(
                                "simulated insert failure"))
                .when(menuMapper)
                .saveRoleMenu(eq(20L), anyList());
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> roleMenus.saveRoleMenu(parm));
        assertEquals(
                100L,
                db.queryForObject(
                        "SELECT menu_id FROM sys_role_menu WHERE role_id=20", Long.class));
    }

    @Test
    void databaseFailureReturns503WithoutReportingExpiredSession() throws Exception {
        String auth = token("admin", 1, "sys:user:list");
        when(sessions.getSession(1L, "test-session"))
                .thenThrow(
                        new org.springframework.data.redis.RedisConnectionFailureException(
                                "test unavailable"));
        mvc.perform(get("/api/sysUser/list").header("Authorization", auth))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503));
    }

    @Test
    void menuCycleCannotBeSaved() throws Exception {
        mvc.perform(
                        put("/api/sysMenu")
                                .header("Authorization", token("admin", 1, "sys:menu:edit"))
                                .header("X-Requested-With", "XMLHttpRequest")
                                .contentType("application/json")
                                .content(
                                        "{\"menuId\":100,\"parentId\":100,\"title\":\"Loop\",\"type\":\"1\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(
                0L,
                db.queryForObject("SELECT parent_id FROM sys_menu WHERE menu_id=100", Long.class));
    }

    @Test
    void userDeletionCleansReferencesBeforeDeletingParent() throws Exception {
        mvc.perform(
                        delete("/api/sysUser/3")
                                .header("Authorization", token("admin", 1, "sys:user:delete"))
                                .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk());
        assertEquals(
                0,
                db.queryForObject("SELECT COUNT(*) FROM sys_user WHERE user_id=3", Integer.class));
        assertEquals(
                0,
                db.queryForObject(
                        "SELECT COUNT(*) FROM sys_user_role WHERE user_id=3", Integer.class));
    }

    @Test
    void roleDeletionCleansReferencesBeforeDeletingParent() throws Exception {
        mvc.perform(
                        delete("/api/role/20")
                                .header("Authorization", token("admin", 1, "sys:role:delete"))
                                .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk());
        assertEquals(
                0,
                db.queryForObject(
                        "SELECT COUNT(*) FROM sys_user_role WHERE role_id=20", Integer.class));
        assertEquals(
                0,
                db.queryForObject(
                        "SELECT COUNT(*) FROM sys_role_menu WHERE role_id=20", Integer.class));
    }

    @Test
    void menuDeletionCleansReferencesBeforeDeletingParent() throws Exception {
        mvc.perform(
                        delete("/api/sysMenu/200")
                                .header("Authorization", token("admin", 1, "sys:menu:delete"))
                                .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk());
        assertEquals(
                0,
                db.queryForObject(
                        "SELECT COUNT(*) FROM sys_role_menu WHERE menu_id=200", Integer.class));
    }

    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    private PermissionCacheService cache() {
        return new PermissionCacheService(
                redis,
                mock(com.itmk.config.security.detailservice.CustomerUserDetailService.class));
    }

    @Test
    void permissionVersionChangesOnlyAfterDatabaseCommit() {
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            db.update("UPDATE sys_role SET role_name='Committed' WHERE role_id=20");
                            cache().invalidateAll();
                            verify(redis, never()).increment(anyString());
                        });
        verify(redis).increment("authz:global-version");
    }

    @Test
    void rolledBackDatabaseChangeDoesNotInvalidatePermissions() {
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            db.update(
                                    "UPDATE sys_role SET role_name='Rolled back' WHERE role_id=20");
                            cache().invalidateAll();
                            status.setRollbackOnly();
                        });
        verify(redis, never()).increment(anyString());
        assertEquals(
                "Ordinary role",
                db.queryForObject("SELECT role_name FROM sys_role WHERE role_id=20", String.class));
    }
}

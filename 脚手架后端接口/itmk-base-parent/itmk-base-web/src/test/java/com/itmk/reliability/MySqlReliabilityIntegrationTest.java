package com.itmk.reliability;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.itmk.config.redis.RedisService;
import com.itmk.config.audit.AuditService;
import com.itmk.config.security.dto.AuthSessionDto;
import com.itmk.config.security.dto.PermissionDto;
import com.itmk.config.security.service.*;
import com.itmk.jwt.JwtUtils;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Uses production Flyway migrations, real MySQL constraints and real Redis sessions. */
@SpringBootTest(properties={"jwt.secret=mysql-integration-tests-secret-at-least-32-bytes",
        "logging.file.name=target/mysql-test.log", "spring.flyway.enabled=true"})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named="auth.mysql.url",matches="jdbc:mysql://[^/]+/jsj_reliability_tests(?:\\?.*)?")
class MySqlReliabilityIntegrationTest {
    @DynamicPropertySource static void settings(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",()->System.getProperty("auth.mysql.url"));
        registry.add("spring.datasource.username",()->System.getProperty("auth.mysql.user","root"));
        registry.add("spring.datasource.password",()->System.getProperty("auth.mysql.password",""));
        registry.add("spring.data.redis.port",()->System.getProperty("auth.redis.port","6379"));
    }
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired Flyway flyway;
    @Autowired JwtUtils jwt;
    @Autowired AuthRedisService sessions;
    @Autowired SecurityStateService states;
    @Autowired PermissionCacheService permissions;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PasswordEncoder encoder;
    @MockitoSpyBean RedisService redis;
    @MockitoSpyBean AuditService audit;
    @MockitoSpyBean com.itmk.web.sys_user.mapper.SecurityStateMapper securityDb;

    @Test void bootstrapAdminRequiresAnEmptyDatabaseAndUsesBCrypt() {
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            db.update("DELETE FROM sys_user_role");
            db.update("DELETE FROM sys_user");
            var bootstrap=new com.itmk.config.security.BootstrapAdmin(securityDb,encoder);
            org.springframework.test.util.ReflectionTestUtils.setField(bootstrap,"username","bootstrap-test");
            org.springframework.test.util.ReflectionTestUtils.setField(bootstrap,"password","isolated-bootstrap-password");
            bootstrap.run(null);
            assertEquals(1,securityDb.countUsers());
            var user=db.queryForMap("SELECT password,is_admin FROM sys_user WHERE username='bootstrap-test'");
            assertTrue(encoder.matches("isolated-bootstrap-password",(String)user.get("password")));
            assertEquals("1",user.get("is_admin").toString());
            assertThrows(IllegalStateException.class,()->bootstrap.run(null));
            status.setRollbackOnly();
        });
    }

    @Test void primaryDatabaseFailureCannotAuthorizeFromCachedPermissions() throws Exception {
        String target=token(9002);
        assertNotNull(permissions.getOrLoad(9002L,"mysql-9002"));
        doThrow(new org.springframework.dao.TransientDataAccessResourceException("primary unavailable"))
            .when(securityDb).read(eq(9002L),any());
        assertThrows(DataAccessException.class,()->permissions.getOrLoad(9002L,"mysql-9002"));
        mvc.perform(get("/api/sysUser/list").header("Authorization",target))
            .andExpect(status().isServiceUnavailable());
    }
    @Test void incompleteAuditIsDetectedAfterFiveMinutesRegardlessOfJvmTimeZone() {
        String id=UUID.randomUUID().toString();
        var now=java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        long before=audit.pendingCount();
        try {
            db.update("INSERT INTO sys_audit_pending(request_id,received_at) VALUES(?,?)",id,now.minusMinutes(6));
            assertEquals(before+1,audit.pendingCount());
            db.update("UPDATE sys_audit_pending SET received_at=? WHERE request_id=?",now,id);
            assertEquals(before,audit.pendingCount());
        } finally { db.update("DELETE FROM sys_audit_pending WHERE request_id=?",id); }
    }

    @BeforeEach void fixture() {
        db.update("DELETE FROM sys_user_role WHERE user_id BETWEEN 9001 AND 9003");
        db.update("DELETE FROM sys_role_menu WHERE role_id=9002");
        db.update("DELETE FROM sys_role WHERE role_id=9002");
        db.update("DELETE FROM sys_user WHERE user_id BETWEEN 9001 AND 9003");
        String hash=encoder.encode("integration-password-strong");
        for (long id=9001;id<=9003;id++)
            db.update("INSERT INTO sys_user(user_id,username,password,is_admin,nick_name) VALUES(?,?,?,?,?)",
                id,"mysql-"+id,hash,id==9001?"1":"0","Test");
        db.update("INSERT INTO sys_role(role_id,role_name) VALUES(9002,'Integration role')");
        db.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(9002,9002)");
        db.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES(9002,2)");
        new TransactionTemplate(transactions).executeWithoutResult(s->states.invalidateAll());
    }
    private String token(long id) {
        String username="mysql-"+id;
        String sid=UUID.randomUUID().toString();
        sessions.createSession(sid,new AuthSessionDto(id,username,System.currentTimeMillis(),states.read(id,null).sessionVersion()),sessions.getSessionIdleTimeout());
        return "Bearer "+jwt.generateToken(Map.of("userId",Long.toString(id),"username",username,"sid",sid));
    }
    @Test void migrationsAreValidatedAndIdempotent() {
        flyway.validate();
        assertEquals(0,flyway.migrate().migrationsExecuted);
        assertEquals("5",flyway.info().current().getVersion().getVersion());
        assertEquals("InnoDB",db.queryForObject("SELECT engine FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='sys_user'",String.class));
    }
    @Test void existingDatabaseCanBeExplicitlyBaselinedWithoutOverwritingMenus() {
        isolatedLegacySchema(false);
    }
    @Test void dirtyExistingDatabaseMigrationFailsWithoutDeletingData() {
        isolatedLegacySchema(true);
    }
    private void isolatedLegacySchema(boolean duplicates) {
        String schema="jsj_reliability_upgrade_"+UUID.randomUUID().toString().replace("-","");
        db.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        String url=System.getProperty("auth.mysql.url").replace("/jsj_reliability_tests","/"+schema);
        var source=new org.springframework.jdbc.datasource.DriverManagerDataSource(url,
            System.getProperty("auth.mysql.user","root"),System.getProperty("auth.mysql.password",""));
        var legacy=new JdbcTemplate(source);
        try {
            Flyway.configure().dataSource(source).locations("classpath:db/migration").target("1").load().migrate();
            legacy.update("INSERT INTO sys_menu(menu_id,parent_id,title,code,type) VALUES(77,0,'Preserve me','custom:menu','1')");
            legacy.execute("DROP TABLE flyway_schema_history");
            if(duplicates) legacy.update("INSERT INTO sys_user(username,password) VALUES('duplicate','hash'),('duplicate','hash')");
            var upgrade=Flyway.configure().dataSource(source).locations("classpath:db/migration").baselineVersion("1").load();
            upgrade.baseline();
            if(duplicates) {
                assertThrows(org.flywaydb.core.api.FlywayException.class,upgrade::migrate);
                assertEquals(2,legacy.queryForObject("SELECT COUNT(*) FROM sys_user",Integer.class));
            } else {
                upgrade.migrate(); upgrade.validate();
                assertEquals("Preserve me",legacy.queryForObject("SELECT title FROM sys_menu WHERE menu_id=77",String.class));
                assertEquals(1,legacy.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE code='sys:audit:list'",Integer.class));
            }
        } finally {
            // This fixed generated prefix can never identify a business database.
            if(!schema.matches("jsj_reliability_upgrade_[a-f0-9]{32}")) throw new IllegalStateException("Unsafe test schema");
            db.execute("DROP DATABASE "+schema);
        }
    }
    @Test void duplicateUsernameAndRelationsAndOrphansAreRejectedByMySql() {
        assertThrows(DataAccessException.class,()->db.update("INSERT INTO sys_user(username,password) VALUES('mysql-9002','hash')"));
        assertThrows(DataAccessException.class,()->db.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(9002,9002)"));
        assertThrows(DataAccessException.class,()->db.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES(9002,2)"));
        assertThrows(DataAccessException.class,()->db.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(9003,9999999)"));
        assertThrows(DataAccessException.class,()->db.update("DELETE FROM sys_role WHERE role_id=9002"));
    }
    @Test void oldRedisPermissionSurvivesButIsUnreachableAfterCommit() throws Exception {
        String target=token(9002),admin=token(9001);
        mvc.perform(get("/api/sysUser/list").header("Authorization",target)).andExpect(status().isOk());
        String oldKey="authz:db-user:{9002}:v"+states.read(9002L,null).cacheVersion();
        assertNotNull(redis.getJson(oldKey,PermissionDto.class));
        // Any attempted Redis invalidation would fail, but the write no longer needs Redis.
        doThrow(new RedisConnectionFailureException("fault injection")).when(redis).increment(anyString());
        mvc.perform(post("/api/role/saveRoleMenu").header("Authorization",admin)
            .header("X-Requested-With","XMLHttpRequest").contentType("application/json")
            .content("{\"roleId\":9002,\"list\":[]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        assertNotNull(redis.getJson(oldKey,PermissionDto.class));
        mvc.perform(get("/api/sysUser/list").header("Authorization",target)).andExpect(status().isForbidden());
        verify(redis,never()).increment(anyString());
    }
    @Test void cacheFailureFallsBackToFreshDatabasePermissions() {
        doThrow(new RedisConnectionFailureException("fault injection")).when(redis).getJson(startsWith("authz:db-user:"),eq(PermissionDto.class));
        doThrow(new RedisConnectionFailureException("fault injection")).when(redis).setJson(startsWith("authz:db-user:"),any(),any());
        assertTrue(permissions.getOrLoad(9002L,"mysql-9002").getPermissions().contains("sys:user:list"));
        new TransactionTemplate(transactions).executeWithoutResult(s->{
            db.update("DELETE FROM sys_role_menu WHERE role_id=9002"); states.invalidateAll();
        });
        assertFalse(permissions.getOrLoad(9002L,"mysql-9002").getPermissions().contains("sys:user:list"));
    }
    @Test void failedBusinessTransactionRollsBackPermissionsAndEpoch() {
        long version=states.read(9002L,null).globalVersion();
        new TransactionTemplate(transactions).executeWithoutResult(s->{
            db.update("DELETE FROM sys_role_menu WHERE role_id=9002"); states.invalidateAll(); s.setRollbackOnly();
        });
        assertEquals(version,states.read(9002L,null).globalVersion());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys_role_menu WHERE role_id=9002",Integer.class));
    }
    @Test void passwordResetRevokesJwtAndRefreshWithoutRedisDeletion() throws Exception {
        String target=token(9002);
        var decoded=jwt.jwtDecode(target.substring(7));
        String sid=decoded.getClaim("sid").asString();
        String refresh=sessions.newRefreshToken(9002L);
        sessions.saveRefreshToken(refresh,new com.itmk.config.security.dto.RefreshTokenDto(9002L,"mysql-9002",sid),java.time.Duration.ofDays(7));
        doThrow(new RedisConnectionFailureException("fault injection")).when(redis).delete(anyList());
        mvc.perform(post("/api/sysUser/resetPassword").header("Authorization",token(9001))
            .header("X-Requested-With","XMLHttpRequest").contentType("application/json")
            .content("{\"userId\":9002,\"password\":\"changed-strong-password\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        assertNotNull(sessions.getSession(9002L,sid));
        mvc.perform(get("/api/sysUser/list").header("Authorization",target)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/refresh").header("X-Requested-With","XMLHttpRequest")
            .cookie(new jakarta.servlet.http.Cookie("refresh_token",refresh)))
            .andExpect(jsonPath("$.code").value(401));
    }
    @Test void durableLogoutRejectsSessionAfterRedisRecovers() throws Exception {
        String target=token(9002);
        doThrow(new RedisConnectionFailureException("fault injection")).when(redis).delete(anyString());
        mvc.perform(post("/api/sysUser/loginOut").header("Authorization",target)
            .header("X-Requested-With","XMLHttpRequest"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/api/sysUser/list").header("Authorization",target)).andExpect(status().isUnauthorized());
    }
    @Test void deletedOrDisabledUserCannotUseCachedAuthorities() throws Exception {
        String target=token(9002);
        permissions.getOrLoad(9002L,"mysql-9002");
        db.update("UPDATE sys_user SET is_enabled=FALSE WHERE user_id=9002");
        mvc.perform(get("/api/sysUser/list").header("Authorization",target)).andExpect(status().isUnauthorized());
        String victim=token(9003);
        db.update("DELETE FROM sys_user WHERE user_id=9003");
        mvc.perform(get("/api/sysUser/getUserInfo").header("Authorization",victim)).andExpect(status().isUnauthorized());
    }
    @Test void auditCapturesBusinessFailureAndPermissionDenialWithoutSecrets() throws Exception {
        var denied=mvc.perform(get("/api/audit/list").header("Authorization",token(9002)))
            .andExpect(status().isForbidden()).andReturn();
        String requestId=denied.getResponse().getHeader("X-Request-ID");
        assertEquals(403,db.queryForObject("SELECT http_status FROM sys_audit_event WHERE request_id=? AND phase='COMPLETED'",Integer.class,requestId));
        var result=mvc.perform(post("/api/sysUser/login").header("X-Requested-With","XMLHttpRequest")
            .contentType("application/json").content("{\"username\":\"sensitive-name\",\"password\":\"secret-no-log\",\"captchaId\":\"absent\",\"code\":\"no\"}"))
            .andExpect(status().isOk()).andReturn();
        String failedId=result.getResponse().getHeader("X-Request-ID");
        assertNotEquals(200,db.queryForObject("SELECT result_code FROM sys_audit_event WHERE request_id=? AND phase='COMPLETED'",Integer.class,failedId));
        assertFalse(db.queryForList("SELECT * FROM sys_audit_event WHERE request_id=?",failedId).toString().contains("secret-no-log"));
    }
    @Test void auditReceiptFailurePreventsBusinessExecution() throws Exception {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("fault injection"))
            .when(audit).append(any(),eq("RECEIVED"),isNull(),isNull(),isNull());
        mvc.perform(delete("/api/sysUser/9003").header("Authorization",token(9001))
            .header("X-Requested-With","XMLHttpRequest")).andExpect(status().isServiceUnavailable());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys_user WHERE user_id=9003",Integer.class));
    }
    @Test void mutationAuditFailureRollsBackBusinessAndPermissionVersion() throws Exception {
        long version=states.read(9002L,null).globalVersion();
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("fault injection"))
            .when(org.springframework.test.util.AopTestUtils.<AuditService>getUltimateTargetObject(audit)).appendMutation(any());
        mvc.perform(post("/api/role/saveRoleMenu").header("Authorization",token(9001))
            .header("X-Requested-With","XMLHttpRequest").contentType("application/json")
            .content("{\"roleId\":9002,\"list\":[]}"))
            .andExpect(status().isServiceUnavailable());
        assertEquals(version,states.read(9002L,null).globalVersion());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys_role_menu WHERE role_id=9002",Integer.class));
    }
    @Test void successfulMutationHasCommittedAuditRecord() throws Exception {
        var result=mvc.perform(delete("/api/sysUser/9003").header("Authorization",token(9001))
            .header("X-Requested-With","XMLHttpRequest")).andExpect(status().isOk()).andReturn();
        String id=result.getResponse().getHeader("X-Request-ID");
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE request_id=? AND phase='CHANGED' AND user_id=9001 AND target_id=9003",Integer.class,id));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM sys_user WHERE user_id=9003",Integer.class));
    }
    @Test void legacySessionWithoutEpochIsRejected() throws Exception {
        String sid=UUID.randomUUID().toString();
        sessions.createSession(sid,new AuthSessionDto(9002L,"mysql-9002",System.currentTimeMillis()),sessions.getSessionIdleTimeout());
        String token=jwt.generateToken(Map.of("userId","9002","username","mysql-9002","sid",sid));
        mvc.perform(get("/api/sysUser/list").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
    @Test void auditOutcomeFailureLeavesDurableReceipt() throws Exception {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("fault injection"))
            .when(audit).append(any(),eq("COMPLETED"),any(),any(),any());
        var result=mvc.perform(get("/api/sysUser/getUserInfo").header("Authorization",token(9002)))
            .andExpect(status().isOk()).andReturn();
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE request_id=?",Integer.class,result.getResponse().getHeader("X-Request-ID")));
    }
    @Test void joinUsesRelationIndexInRealMySql() {
        String plan=db.queryForList("EXPLAIN SELECT rm.menu_id FROM sys_user_role ur JOIN sys_role_menu rm ON rm.role_id=ur.role_id WHERE ur.user_id=9002").toString();
        assertTrue(plan.contains("uk_sys_user_role"));
        assertTrue(plan.contains("uk_sys_role_menu"));
    }
}

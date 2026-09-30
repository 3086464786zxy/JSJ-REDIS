package com.itmk.config.security;
import com.itmk.config.security.service.PasswordPolicy;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import com.itmk.web.sys_user.mapper.SecurityStateMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
@ConditionalOnProperty(name="app.bootstrap.enabled",havingValue="true")
public class BootstrapAdmin implements ApplicationRunner {
    private final SecurityStateMapper db;
    private final PasswordEncoder encoder;
    @Value("${app.bootstrap.username:admin}") private String username;
    @Value("${app.bootstrap.password}") private String password;
    public BootstrapAdmin(SecurityStateMapper db, PasswordEncoder encoder) { this.db=db; this.encoder=encoder; }
    @Override @Transactional(rollbackFor=Exception.class)
    public void run(ApplicationArguments args) {
        PasswordPolicy.validate(password);
        if (username==null || username.isBlank() || username.length()>64) throw new IllegalArgumentException("初始化账户名无效");
        // Lock singleton to avoid two instances bootstrapping different administrators concurrently.
        if(db.lockAuthorizationState()==null) throw new IllegalStateException("权限版本状态不存在");
        if (db.countUsers()!=0)
            throw new IllegalStateException("数据库已有用户，必须关闭管理员初始化开关");
        if(db.insertBootstrapAdmin(username,encoder.encode(password))!=1)
            throw new IllegalStateException("管理员初始化失败");
    }
}

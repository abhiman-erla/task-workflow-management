package com.abhiman.taskflow.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Registration only creates MEMBERs, so someone has to be the first ADMIN.
 * On startup, if no admin exists, create one from app.bootstrap-admin.* (env vars in production).
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapAdminProperties properties;

    public AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder, BootstrapAdminProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.ADMIN) || properties.email() == null || properties.password() == null) {
            return;
        }
        String email = properties.email().trim().toLowerCase(Locale.ROOT);
        users.save(new User("Administrator", email, passwordEncoder.encode(properties.password()), Role.ADMIN));
        log.info("Created bootstrap admin account {}", email);
    }

    @ConfigurationProperties(prefix = "app.bootstrap-admin")
    public record BootstrapAdminProperties(String email, String password) {
    }
}

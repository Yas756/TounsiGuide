package com.tounsiguide.identity.config;

import com.tounsiguide.identity.domain.Role;
import com.tounsiguide.identity.domain.User;
import com.tounsiguide.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Locale;

/** Crée un administrateur initial si ADMIN_EMAIL et ADMIN_PASSWORD sont définis. */
@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final SecurityProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(SecurityProperties properties, UserRepository userRepository,
                          PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        SecurityProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        if (admin == null || isBlank(admin.email()) || isBlank(admin.password())) {
            return;
        }
        String email = admin.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(new User(email, "Administrateur", passwordEncoder.encode(admin.password()),
                EnumSet.of(Role.MEMBER, Role.ADMIN)));
        log.info("Administrateur initial créé : {}", email);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

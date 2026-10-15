package com.abhiman.taskflow.user;

import com.abhiman.taskflow.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository users;

    @Test
    void findsUserByEmailIgnoringCase() {
        users.save(new User("Ada Lovelace", "ada@example.com", "hash", Role.MEMBER));

        assertThat(users.findByEmailIgnoreCase("ADA@Example.com"))
                .get()
                .extracting(User::getFullName)
                .isEqualTo("Ada Lovelace");
        assertThat(users.existsByEmailIgnoreCase("nobody@example.com")).isFalse();
    }

    @Test
    void databaseRejectsDuplicateEmail() {
        users.saveAndFlush(new User("One", "dup@example.com", "hash", Role.MEMBER));

        assertThatThrownBy(() -> users.saveAndFlush(new User("Two", "dup@example.com", "hash", Role.MEMBER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByRole() {
        assertThat(users.existsByRole(Role.ADMIN)).isFalse();
        users.save(new User("Root", "root@example.com", "hash", Role.ADMIN));
        assertThat(users.existsByRole(Role.ADMIN)).isTrue();
    }
}

package com.abhiman.taskflow.user;

import com.abhiman.taskflow.common.NotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public User get(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public User changeRole(Long id, Role role) {
        User user = get(id);
        user.setRole(role); // dirty checking: Hibernate writes the UPDATE at commit, no save() needed
        return user;
    }
}

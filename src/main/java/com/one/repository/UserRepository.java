package com.one.repository;


import com.one.domain.UserRole;
import com.one.modal.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Set;

public interface UserRepository extends JpaRepository<User, Long> {

    User findByEmail(String email);
    Set<User> findByRole(UserRole role);
}

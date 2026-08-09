package com.one.repository;

import com.one.modal.NotificationSettings;
import com.one.modal.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Long> {

    // Find settings by user
    Optional<NotificationSettings> findByUser(User user);

    // Check if settings exist for user
    boolean existsByUser(User user);
}

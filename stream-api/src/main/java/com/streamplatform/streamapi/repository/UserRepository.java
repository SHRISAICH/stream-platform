package com.streamplatform.streamapi.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.streamplatform.streamapi.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    long countByEnabledTrue();

    java.util.List<User> findAllByOrderByCreatedAtDesc();
}
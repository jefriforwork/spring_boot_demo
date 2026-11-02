package com.example.demo.repository;

import com.example.demo.entity.Invite;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface InviteRepository extends JpaRepository<Invite, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Invite> findByTokenHash(String tokenHash);
}

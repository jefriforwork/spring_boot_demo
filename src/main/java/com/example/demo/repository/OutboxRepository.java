package com.example.demo.repository;

import com.example.demo.entity.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

    List<OutboxMessage> findTop20ByPublishedAtIsNullOrderByIdAsc();
}

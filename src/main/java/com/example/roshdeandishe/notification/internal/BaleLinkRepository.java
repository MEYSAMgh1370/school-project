package com.example.roshdeandishe.notification.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface BaleLinkRepository extends JpaRepository<BaleLinkEntity, Long> {

    Optional<BaleLinkEntity> findByUserId(Long userId);

    Optional<BaleLinkEntity> findByChatId(Long chatId);
}

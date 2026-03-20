package com.porcelain_shutter.bot.repository;

import com.porcelain_shutter.bot.entity.RegisteredChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegisteredChatRepository extends JpaRepository<RegisteredChat, Long> {

    List<RegisteredChat> findAllByActiveTrue();
}
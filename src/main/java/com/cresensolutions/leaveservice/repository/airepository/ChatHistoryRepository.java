package com.cresensolutions.leaveservice.repository.airepository;

import com.cresensolutions.leaveservice.entity.aientity.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {
}

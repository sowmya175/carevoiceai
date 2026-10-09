package com.carevoice.repository;

import com.carevoice.domain.SessionPlanQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionPlanQuestionRepository extends JpaRepository<SessionPlanQuestion, Long> {
    List<SessionPlanQuestion> findBySession_IdOrderByDisplayOrderAsc(Long sessionId);
}

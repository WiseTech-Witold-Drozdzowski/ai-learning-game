package com.careercoach.coach.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.careercoach.coach.domain.MockSession;

/** Persistence for {@link MockSession} envelopes. */
public interface MockSessionRepository extends JpaRepository<MockSession, Long> {
}

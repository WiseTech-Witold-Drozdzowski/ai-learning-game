package com.careercoach.coach.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.careercoach.coach.domain.MockMessage;

/** Persistence for {@link MockMessage} transcript entries, ordered by {@code seq}. */
public interface MockMessageRepository extends JpaRepository<MockMessage, Long> {

    /** The full transcript of a session, in turn order. */
    List<MockMessage> findBySessionIdOrderBySeqAsc(Long sessionId);

    /** The highest {@code seq} already written for a session (for the next incremental append). */
    Optional<MockMessage> findFirstBySessionIdOrderBySeqDesc(Long sessionId);
}

package com.careercoach.gamification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.careercoach.config.repository.SkillDefinitionRepository;
import com.careercoach.gamification.domain.ExpEvent;
import com.careercoach.gamification.mapper.SkillMapper;
import com.careercoach.gamification.repository.ExpEventRepository;
import com.careercoach.gamification.repository.SkillRepository;
import com.careercoach.gamification.web.model.ExpEventView;

/**
 * Unit tests for {@link ProfileQueryService#listSkillHistory} (issue-7) — repository and mapper are mocked.
 * Red phase: the skeleton throws {@code NotImplementedException}.
 */
@ExtendWith(MockitoExtension.class)
class ProfileQueryServiceHistoryTest {

    @Mock
    private ExpEventRepository expEventRepository;

    @Mock
    private SkillMapper skillMapper;

    @Mock
    private CareerProfileService careerProfileService;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private SkillDefinitionRepository skillDefinitionRepository;

    private ProfileQueryService service;

    @BeforeEach
    void setUp() {
        service = new ProfileQueryService(careerProfileService, skillRepository, skillDefinitionRepository, skillMapper,
                expEventRepository);
    }

    @Test
    void listSkillHistory_shouldReturnMappedEventsInRepositoryOrder() {
        // Arrange
        Instant now = Instant.now();
        ExpEvent event1 = ExpEvent.builder()
                .sourceTaskId(100L)
                .attemptId(1L)
                .skillKey("JAVA")
                .amount(30L)
                .reason("task-complete")
                .createdAt(now)
                .build();
        ExpEvent event2 = ExpEvent.builder()
                .sourceTaskId(101L)
                .attemptId(2L)
                .skillKey("JAVA")
                .amount(20L)
                .reason("task-complete")
                .createdAt(now)
                .build();
        ExpEventView view1 = new ExpEventView(100L, 1L, 30L, "task-complete", now);
        ExpEventView view2 = new ExpEventView(101L, 2L, 20L, "task-complete", now);

        when(expEventRepository.findBySkillKeyOrderByCreatedAtAsc("JAVA")).thenReturn(List.of(event1, event2));
        when(skillMapper.toExpEventView(event1)).thenReturn(view1);
        when(skillMapper.toExpEventView(event2)).thenReturn(view2);

        // Act
        List<ExpEventView> result = service.listSkillHistory("JAVA");

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).sourceTaskId()).isEqualTo(100L);
        assertThat(result.get(1).sourceTaskId()).isEqualTo(101L);
        assertThat(result.get(0).amount()).isEqualTo(30L);
        assertThat(result.get(1).amount()).isEqualTo(20L);
    }

    @Test
    void listSkillHistory_shouldReturnEmptyList_whenSkillHasNoEvents() {
        // Arrange
        when(expEventRepository.findBySkillKeyOrderByCreatedAtAsc("JAVA")).thenReturn(List.of());

        // Act
        List<ExpEventView> result = service.listSkillHistory("JAVA");

        // Assert
        assertThat(result).isEmpty();
        verify(skillMapper, never()).toExpEventView(any());
    }
}

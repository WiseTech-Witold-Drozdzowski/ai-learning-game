package com.careercoach.gamification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.mapstruct.factory.Mappers;

import com.careercoach.config.domain.SkillDefinition;
import com.careercoach.config.repository.SkillDefinitionRepository;
import com.careercoach.gamification.domain.AvatarState;
import com.careercoach.gamification.domain.AvatarTier;
import com.careercoach.gamification.domain.CareerProfile;
import com.careercoach.gamification.domain.Skill;
import com.careercoach.gamification.mapper.SkillMapper;
import com.careercoach.gamification.repository.ExpEventRepository;
import com.careercoach.gamification.repository.SkillRepository;
import com.careercoach.gamification.web.model.ProfileResponse;
import com.careercoach.gamification.web.model.SkillView;

/**
 * Unit tests for {@link ProfileQueryService} (issue-6) — all collaborators are mocked.
 * Red phase: the skeleton throws {@code UnsupportedOperationException}.
 */
@ExtendWith(MockitoExtension.class)
class ProfileQueryServiceTest {

    @Mock
    private CareerProfileService careerProfileService;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private SkillDefinitionRepository skillDefinitionRepository;

    @Mock
    private ExpEventRepository expEventRepository;

    private ProfileQueryService service;

    @BeforeEach
    void setUp() {
        SkillMapper skillMapper = Mappers.getMapper(SkillMapper.class);
        service = new ProfileQueryService(
                careerProfileService, skillRepository, skillDefinitionRepository, skillMapper, expEventRepository);
    }

    @Test
    void getProfile_shouldReturnTotalsAndMappedSkills_whenUserHasSkills() {
        // Arrange
        CareerProfile profile = new CareerProfile(1L, 120L, 2, new AvatarState(AvatarTier.SILVER, List.of()));
        when(careerProfileService.getForUser(1L)).thenReturn(profile);
        when(skillRepository.findAllByOrderByKeyAsc())
                .thenReturn(List.of(Skill.builder().key("JAVA").level(2).exp(120L).build()));
        when(skillDefinitionRepository.findAll())
                .thenReturn(List.of(SkillDefinition.builder().key("JAVA").displayName("Java").category("lang").build()));

        // Act
        ProfileResponse result = service.getProfile(1L);

        // Assert
        assertThat(result.totalExp()).isEqualTo(120L);
        assertThat(result.level()).isEqualTo(2);
        assertThat(result.avatarState().tier()).isEqualTo(AvatarTier.SILVER);
        assertThat(result.skills()).hasSize(1);
        assertThat(result.skills().get(0).key()).isEqualTo("JAVA");
        assertThat(result.skills().get(0).displayName()).isEqualTo("Java");
        assertThat(result.skills().get(0).level()).isEqualTo(2);
        assertThat(result.skills().get(0).exp()).isEqualTo(120L);
    }

    @Test
    void getProfile_shouldReturnEmptySkills_whenUserHasNoSkills() {
        // Arrange
        CareerProfile profile = new CareerProfile(1L, 0L, 1, AvatarState.initial());
        when(careerProfileService.getForUser(1L)).thenReturn(profile);
        when(skillRepository.findAllByOrderByKeyAsc()).thenReturn(List.of());
        when(skillDefinitionRepository.findAll()).thenReturn(List.of());

        // Act
        ProfileResponse result = service.getProfile(1L);

        // Assert
        assertThat(result.skills()).isEmpty();
    }

    @Test
    void getProfile_shouldFallBackToKey_whenSkillDefinitionMissing() {
        // Arrange
        CareerProfile profile = new CareerProfile(1L, 10L, 1, AvatarState.initial());
        when(careerProfileService.getForUser(1L)).thenReturn(profile);
        when(skillRepository.findAllByOrderByKeyAsc())
                .thenReturn(List.of(Skill.builder().key("UNKNOWN_SKILL").level(1).exp(10L).build()));
        when(skillDefinitionRepository.findAll()).thenReturn(List.of());

        // Act
        ProfileResponse result = service.getProfile(1L);

        // Assert
        assertThat(result.skills()).hasSize(1);
        assertThat(result.skills().get(0).displayName()).isEqualTo("UNKNOWN_SKILL");
    }

    @Test
    void listSkills_shouldReturnAllSkillsOrderedByKey_withLevelAndExp() {
        // Arrange
        when(skillRepository.findAllByOrderByKeyAsc()).thenReturn(List.of(
                Skill.builder().key("JAVA").level(2).exp(120L).build(),
                Skill.builder().key("SPRING").level(1).exp(30L).build()));
        when(skillDefinitionRepository.findAll()).thenReturn(List.of(
                SkillDefinition.builder().key("JAVA").displayName("Java").category("lang").build(),
                SkillDefinition.builder().key("SPRING").displayName("Spring").category("framework").build()));

        // Act
        List<SkillView> result = service.listSkills();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).key()).isEqualTo("JAVA");
        assertThat(result.get(0).level()).isEqualTo(2);
        assertThat(result.get(0).exp()).isEqualTo(120L);
        assertThat(result.get(1).key()).isEqualTo("SPRING");
        assertThat(result.get(1).exp()).isEqualTo(30L);
    }
}

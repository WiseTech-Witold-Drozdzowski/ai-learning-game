package com.careercoach.config.seed;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.careercoach.config.domain.VerificationMethod;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "careercoach.config")
@Getter
@Setter
public class ConfigSeedProperties {

    private List<TaskTypeSeed> taskTypes;

    private List<SkillSeed> skills;

    public record TaskTypeSeed(
            String key,
            String displayName,
            VerificationMethod verificationMethod,
            int expBase,
            boolean expScaleByScore,
            boolean requiresArtifact) {
    }

    public record SkillSeed(String key, String displayName, String category) {
    }
}

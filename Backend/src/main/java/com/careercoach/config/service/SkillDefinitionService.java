package com.careercoach.config.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careercoach.config.domain.SkillDefinition;
import com.careercoach.config.repository.SkillDefinitionRepository;
import com.careercoach.config.web.SkillUpsertRequest;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class SkillDefinitionService {

    private final SkillDefinitionRepository skillDefinitionRepository;

    @Transactional(readOnly = true)
    public List<SkillDefinition> list() {
        return skillDefinitionRepository.findAll(Sort.by("key"));
    }

    @Transactional(readOnly = true)
    public SkillDefinition get(String key) {
        return skillDefinitionRepository.findById(key)
                .orElseThrow(() -> new ConfigEntryNotFoundException("No skill definition for key: " + key));
    }

    public SkillDefinition upsert(String key, SkillUpsertRequest req) {
        SkillDefinition def = skillDefinitionRepository.findById(key)
                .orElseGet(() -> SkillDefinition.builder().key(key).build());
        def.setDisplayName(req.displayName());
        def.setCategory(req.category());
        return skillDefinitionRepository.save(def);
    }

    public boolean seedIfAbsent(SkillDefinition def) {
        if (skillDefinitionRepository.existsById(def.getKey())) {
            return false;
        }
        skillDefinitionRepository.save(def);
        return true;
    }
}

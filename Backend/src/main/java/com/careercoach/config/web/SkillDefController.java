package com.careercoach.config.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careercoach.config.domain.SkillDefinition;
import com.careercoach.config.service.SkillDefinitionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/skill-defs")
@RequiredArgsConstructor
public class SkillDefController {

    private final SkillDefinitionService skillDefinitionService;

    @GetMapping
    public List<SkillDefinition> list() {
        return skillDefinitionService.list();
    }

    @GetMapping("/{key}")
    public SkillDefinition get(@PathVariable String key) {
        return skillDefinitionService.get(key);
    }

    @PutMapping("/{key}")
    public SkillDefinition upsert(@PathVariable String key, @Valid @RequestBody SkillUpsertRequest req) {
        return skillDefinitionService.upsert(key, req);
    }
}

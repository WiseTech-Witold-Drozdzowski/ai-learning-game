package com.careercoach.gamification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "skill")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Skill {

    @Id
    private String key;

    @Column(nullable = false)
    private int level;

    @Column(name = "exp", nullable = false)
    private long exp;

    public static Skill createNew(String key) {
        return Skill.builder().key(key).level(1).exp(0L).build();
    }
}

package com.careercoach.gamification.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "career_profile")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CareerProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "total_exp", nullable = false)
    private long totalExp;

    @Column(nullable = false)
    private int level;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "avatar_state", nullable = false, columnDefinition = "jsonb")
    private AvatarState avatarState;
}

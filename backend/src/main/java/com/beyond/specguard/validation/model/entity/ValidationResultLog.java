package com.beyond.specguard.validation.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name="validation_result_log"
)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@Getter
@ToString
public class ValidationResultLog {
    @Id
    @GeneratedValue(strategy= GenerationType.UUID)
    @Column(length = 36, columnDefinition = "CHAR(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name="validation_result_id",
            nullable = false,
            columnDefinition = "CHAR(36)",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private ValidationResult validationResult;


    @Column(name = "validation_score", nullable = false)
    private Double validationScore;


    @Column(name = "keyword_list", columnDefinition = "TEXT", nullable = true)
    private String keywordList;

    @Column(name="mismatch_fields", columnDefinition = "JSON", nullable = true)
    private String mismatchFields;

    @Column(name = "match_fields", columnDefinition = "TEXT")
    private String matchFields;

    @CreationTimestamp
    @Column(name = "validated_at", nullable = false)
    private LocalDateTime validatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "log_type", nullable = false, length = 40)
    private ValidationLogType logType;

    public enum ValidationLogType {
        GITHUB_REPO_COUNT,
        GITHUB_COMMIT_COUNT,
        GITHUB_KEYWORD_MATCH,
        GITHUB_TOPIC_MATCH,
        NOTION_KEYWORD_MATCH,
        VELOG_KEYWORD_MATCH,
        VELOG_POST_COUNT,
        VELOG_RECENT_ACTIVITY,
        CERTIFICATE_MATCH
    }


}

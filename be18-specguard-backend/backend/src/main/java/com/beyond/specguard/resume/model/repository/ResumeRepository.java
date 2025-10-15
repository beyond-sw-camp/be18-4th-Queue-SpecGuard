package com.beyond.specguard.resume.model.repository;

import com.beyond.specguard.resume.model.entity.Resume;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResumeRepository extends JpaRepository<Resume, UUID>, JpaSpecificationExecutor<Resume> {

    @Query("""
        select r.id
        from Resume r
        where r.template.id in :templateIds
          and not exists (
            select 1 from CompanyFormSubmission s
            where s.resume = r
          )
    """)
    List<UUID> findUnsubmittedIdsByTemplateIds(List<UUID> templateIds, Pageable pageable);

    @Query("select r.id from Resume r where r.status <> 'PROCESSING'")
    List<UUID> findUnprocessedResumeIds();

    @Query("SELECT r FROM Resume r JOIN FETCH r.template WHERE r.email = :email AND r.template.id = :templateId")
    Optional<Resume> findByEmailAndTemplateId(@Param("email") String email, @Param("templateId") UUID templateId);

    @Query("""
    select r.id
    from Resume r
    where r.status not in ('PROCESSING', 'VALIDATED')
      and r.updatedAt >= :cutoff
""")
    List<UUID> findUnprocessedResumeIdsSince(@Param("cutoff") LocalDateTime cutoff);
    boolean existsByEmailAndTemplateId(String email, UUID templateId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Resume r set r.status = :status where r.id = :resumeId")
    void updateStatus(@Param("resumeId") UUID id, @Param("status") Resume.ResumeStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Resume r set r.status = :status where r.id = :resumeId")
    int updateStatusValidation(@Param("resumeId") UUID id, @Param("status") Resume.ResumeStatus status);


    UUID id(UUID id);

    @Override
    @EntityGraph(attributePaths = {"validationResult"})
    Page<Resume> findAll(Specification<Resume> spec, Pageable pageable);

    // 전수 조사를 위한 메소드 추가
    @Query("""
    select r.id
    from Resume r
    where r.status not in :statuses
""")
    List<UUID> findAllByStatusNotIn(@Param("statuses") Collection<Resume.ResumeStatus> statuses);


}

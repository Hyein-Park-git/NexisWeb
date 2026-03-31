package com.nexis.web.repository;

import com.nexis.web.entity.HostTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

// 호스트-템플릿 연결(N:M 매핑) 테이블을 다루는 Repository
@Repository
public interface HostTemplateRepository extends JpaRepository<HostTemplateEntity, Long> {

    // 특정 호스트에 연결된 템플릿 목록 (호스트 상세 페이지용)
    List<HostTemplateEntity> findByHostId(Long hostId);

    // 특정 템플릿에 연결된 호스트 목록 (템플릿 적용/동기화 시 사용)
    List<HostTemplateEntity> findByTemplateId(Long templateId);

    // 특정 호스트-템플릿 연결 단건 조회
    Optional<HostTemplateEntity> findByHostIdAndTemplateId(Long hostId, Long templateId);

    // 연결 여부 확인 (중복 연결 방지)
    boolean existsByHostIdAndTemplateId(Long hostId, Long templateId);

    // 호스트 삭제 시 연결 매핑 전부 제거
    @Transactional
    void deleteByHostId(Long hostId);

    // unlink 모드: 특정 호스트-템플릿 연결만 제거
    @Transactional
    void deleteByHostIdAndTemplateId(Long hostId, Long templateId);

    // 템플릿 삭제 시 연결 매핑 전부 제거
    @Transactional
    void deleteByTemplateId(Long templateId);
}
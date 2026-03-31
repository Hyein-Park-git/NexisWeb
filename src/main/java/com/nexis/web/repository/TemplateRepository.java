package com.nexis.web.repository;

import com.nexis.web.entity.TemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TemplateRepository extends JpaRepository<TemplateEntity, Long> {

    // 이름 오름차순 전체 조회 (목록 페이지용)
    List<TemplateEntity> findAllByOrderByNameAsc();

    // 템플릿 이름 중복 체크
    boolean existsByName(String name);
}
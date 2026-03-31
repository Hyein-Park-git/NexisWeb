package com.nexis.web.config;

import com.nexis.web.entity.*;
import com.nexis.web.repository.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

// ApplicationRunner: 앱이 완전히 뜬 직후 run()을 자동 실행해주는 인터페이스
// 초기 데이터 세팅처럼 "앱 시작 시 딱 한 번 실행할 로직"에 사용
@Component
public class DefaultGroupInitializer implements ApplicationRunner {

    private final HostGroupRepository           hostGroupRepository;
    private final UserGroupRepository           userGroupRepository;
    private final UserGroupPermissionRepository permissionRepository;
    private final UserGroupHostAccessRepository hostAccessRepository;

    public DefaultGroupInitializer(HostGroupRepository           hostGroupRepository,
                                   UserGroupRepository           userGroupRepository,
                                   UserGroupPermissionRepository permissionRepository,
                                   UserGroupHostAccessRepository hostAccessRepository) {
        this.hostGroupRepository  = hostGroupRepository;
        this.userGroupRepository  = userGroupRepository;
        this.permissionRepository = permissionRepository;
        this.hostAccessRepository = hostAccessRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try {
            // 1. "Default Group" 호스트 그룹 보장
            // orElseGet: DB에 이미 있으면 그걸 쓰고, 없으면 새로 만들어서 저장
            HostGroupEntity hostGroup = hostGroupRepository.findByName("Default Group")
                .orElseGet(() -> {
                    HostGroupEntity g = new HostGroupEntity();
                    g.setName("Default Group");
                    g.setDescription("Default host group");
                    return hostGroupRepository.save(g);
                });

            // 2. "Default Group" 유저 그룹 보장 (위와 동일한 패턴)
            UserGroupEntity userGroup = userGroupRepository.findByName("Default Group")
                .orElseGet(() -> {
                    UserGroupEntity g = new UserGroupEntity();
                    g.setName("Default Group");
                    g.setDescription("Default user group");
                    g.setEnabled(true);
                    return userGroupRepository.save(g);
                });

            // 3. 유저 그룹 권한이 아직 없을 때만 기본값 세팅 (중복 방지)
            if (permissionRepository.findByGroupId(userGroup.getId()).isEmpty()) {

                // dashboard, monitoring, configuration → 읽기(read)만 허용
                for (String resource : new String[]{"dashboard", "monitoring", "configuration"}) {
                    UserGroupPermissionEntity p = new UserGroupPermissionEntity();
                    p.setGroupId(userGroup.getId());
                    p.setResource(resource);
                    p.setAccessLevel("read");
                    permissionRepository.save(p);
                }

                // administration → 접근 불가(none)
                UserGroupPermissionEntity admin = new UserGroupPermissionEntity();
                admin.setGroupId(userGroup.getId());
                admin.setResource("administration");
                admin.setAccessLevel("none");
                permissionRepository.save(admin);
            }

            // 4. 유저 그룹 ↔ 호스트 그룹 접근 매핑이 없으면 연결
            if (!hostAccessRepository.existsByGroupIdAndHostGroupId(userGroup.getId(), hostGroup.getId())) {
                UserGroupHostAccessEntity a = new UserGroupHostAccessEntity();
                a.setGroupId(userGroup.getId());
                a.setHostGroupId(hostGroup.getId());
                hostAccessRepository.save(a);
            }

        } catch (Exception e) {
            // 설치 전이라 DB가 없는 상태에서 앱이 뜰 수 있음
            // 이때 예외가 발생해도 앱 시작을 막으면 안 되므로 조용히 무시
        }
    }
}
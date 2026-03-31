package com.nexis.web.service;

import com.nexis.web.entity.UserGroupHostAccessEntity;
import com.nexis.web.entity.UserGroupMemberEntity;
import com.nexis.web.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    private final UserRepository                userRepository;
    private final UserGroupMemberRepository     memberRepository;
    private final UserGroupPermissionRepository permissionRepository;
    private final UserGroupHostAccessRepository hostAccessRepository;

    public PermissionService(UserRepository                userRepository,
                             UserGroupMemberRepository     memberRepository,
                             UserGroupPermissionRepository permissionRepository,
                             UserGroupHostAccessRepository hostAccessRepository) {
        this.userRepository       = userRepository;
        this.memberRepository     = memberRepository;
        this.permissionRepository = permissionRepository;
        this.hostAccessRepository = hostAccessRepository;
    }

    // Spring Security의 SecurityContext에서 현재 로그인 유저명 추출
    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    // DB에서 현재 유저의 Role 조회
    private String currentRole() {
        var user = userRepository.findByUsername(currentUsername()).orElse(null);
        return user != null ? user.getRole() : "User";
    }

    private boolean isAdministrator() {
        return "Administrator".equals(currentRole());
    }

    // 권한 문자열 → 정수 변환 (비교 용도)
    private int levelToInt(String level) {
        return switch (level) {
            case "write" -> 2;
            case "read"  -> 1;
            default      -> 0; // "none"
        };
    }

    // 정수 → 권한 문자열 변환
    private String intToLevel(int level) {
        return switch (level) {
            case 2  -> "write";
            case 1  -> "read";
            default -> "none";
        };
    }

    // Role 기반 기본 권한 레벨 반환
    // administration은 Role로 부여하지 않고 User Group으로만 부여
    private int defaultLevelByRole(String role, String resource) {
        if ("Administrator".equals(role)) return 2;
        if ("administration".equals(resource)) return 0;
        return switch (role) {
            case "Operator" -> 2; // write
            case "User"     -> 1; // read
            default         -> 0; // none
        };
    }

    // 특정 리소스에 대한 현재 유저의 최종 권한 레벨 반환
    // Role 기본 권한과 UserGroup 권한 중 더 높은 것을 적용 (최대값 원칙)
    public String getAccessLevel(String resource) {
        String role = currentRole();

        if ("Administrator".equals(role)) return "write"; // Administrator는 항상 write

        var user = userRepository.findByUsername(currentUsername()).orElse(null);
        if (user == null) return "none";

        int baseLevel = defaultLevelByRole(role, resource);

        // 소속된 모든 UserGroup의 권한 중 가장 높은 것을 취함
        List<UserGroupMemberEntity> memberships = memberRepository.findByUserId(user.getId());
        int groupLevel = 0;
        for (UserGroupMemberEntity m : memberships) {
            var perm = permissionRepository.findByGroupIdAndResource(m.getGroupId(), resource).orElse(null);
            if (perm == null) continue;
            int permLevel = levelToInt(perm.getAccessLevel());
            if (permLevel > groupLevel) groupLevel = permLevel;
        }

        // Role 권한 vs Group 권한 중 더 높은 것 최종 적용
        int finalLevel = Math.max(baseLevel, groupLevel);
        return intToLevel(finalLevel);
    }

    // 편의 메서드
    public boolean canRead(String resource)  { return !"none".equals(getAccessLevel(resource)); }
    public boolean canWrite(String resource) { return "write".equals(getAccessLevel(resource)); }

    // 현재 유저가 접근 가능한 호스트 그룹 ID 목록 반환
    // 반환값 의미:
    //   null      → 권한 없음 (접근 불가)
    //   빈 리스트  → Administrator (전체 허용)
    //   [-1L]     → UserGroup 없음 (Default Group만 허용)
    //   [1L, 2L]  → 해당 HostGroup ID만 허용
    public List<Long> getAllowedHostGroupIds() {
        if (isAdministrator()) return List.of(); // 빈 리스트 = 전체 허용

        var user = userRepository.findByUsername(currentUsername()).orElse(null);
        if (user == null) return null;

        List<UserGroupMemberEntity> memberships = memberRepository.findByUserId(user.getId());

        // UserGroup 소속이 없으면 -1L 반환 → Default Group만 접근
        if (memberships.isEmpty()) return List.of(-1L);

        // 하나라도 hostAccess 설정이 없으면 전체 허용
        for (UserGroupMemberEntity m : memberships) {
            List<UserGroupHostAccessEntity> accesses = hostAccessRepository.findByGroupId(m.getGroupId());
            if (accesses.isEmpty()) return List.of();
        }

        // 소속 그룹들의 허용 HostGroup ID 목록 수집 (중복 제거)
        return memberships.stream()
            .flatMap(m -> hostAccessRepository.findByGroupId(m.getGroupId()).stream())
            .map(UserGroupHostAccessEntity::getHostGroupId)
            .distinct()
            .collect(Collectors.toList());
    }

    // 현재 유저의 모든 리소스 권한 맵 반환 (뷰에서 일괄 사용 시 유용)
    public java.util.Map<String, String> getPermissionMap() {
        var map = new java.util.HashMap<String, String>();
        for (String r : new String[]{"dashboard", "monitoring", "configuration", "administration"}) {
            map.put(r, getAccessLevel(r));
        }
        return map;
    }
}
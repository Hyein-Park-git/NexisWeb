package com.nexis.web.service;

import com.nexis.web.entity.HostEntity;
import com.nexis.web.entity.HostGroupMemberEntity;
import com.nexis.web.repository.HostGroupMemberRepository;
import com.nexis.web.repository.HostGroupRepository;
import com.nexis.web.repository.HostRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class HostService {

    private final HostRepository            hostRepository;
    private final HostGroupRepository       hostGroupRepository;
    private final HostGroupMemberRepository hostGroupMemberRepository;
    private final PermissionService         permissionService;

    public HostService(HostRepository            hostRepository,
                       HostGroupRepository       hostGroupRepository,
                       HostGroupMemberRepository hostGroupMemberRepository,
                       PermissionService         permissionService) {
        this.hostRepository            = hostRepository;
        this.hostGroupRepository       = hostGroupRepository;
        this.hostGroupMemberRepository = hostGroupMemberRepository;
        this.permissionService         = permissionService;
    }

    // 권한 무관하게 전체 호스트 조회 (내부용)
    public List<HostEntity> getAllHosts() {
        return hostRepository.findAllByOrderByHostnameAsc();
    }

    // 현재 로그인 유저의 권한에 따라 접근 가능한 호스트만 반환
    public List<HostEntity> getAllowedHosts() {
        List<Long> allowedGroupIds = permissionService.getAllowedHostGroupIds();

        if (allowedGroupIds == null) return List.of();           // 권한 없음 → 빈 리스트
        if (allowedGroupIds.isEmpty()) {                         // Administrator → 전체 반환
            return hostRepository.findAllByOrderByHostnameAsc();
        }

        // Default Group에 속한 호스트는 항상 포함
        List<Long> defaultGroupIds = hostGroupRepository.findByName("Default Group")
            .map(g -> List.of(g.getId()))
            .orElse(List.of());

        // -1L(그룹 없음) 제외, 실제 그룹 ID만 추출
        List<Long> validGroupIds = allowedGroupIds.stream()
            .filter(id -> id > 0)
            .collect(Collectors.toList());

        // Default Group + 허용 그룹 합산
        List<Long> allAllowedGroupIds = new ArrayList<>();
        allAllowedGroupIds.addAll(defaultGroupIds);
        allAllowedGroupIds.addAll(validGroupIds);

        if (allAllowedGroupIds.isEmpty()) return List.of();

        // 허용된 그룹에 속한 호스트 ID 목록 수집 (중복 제거)
        List<Long> allowedHostIds = allAllowedGroupIds.stream()
            .distinct()
            .flatMap(gid -> hostGroupMemberRepository.findByGroupId(gid).stream())
            .map(m -> m.getHostId())
            .filter(hid -> hid != null)
            .distinct()
            .collect(Collectors.toList());

        if (allowedHostIds.isEmpty()) return List.of();

        // 전체 호스트 중 허용된 것만 필터링해서 반환
        return hostRepository.findAllByOrderByHostnameAsc().stream()
            .filter(h -> allowedHostIds.contains(h.getId()))
            .collect(Collectors.toList());
    }

    // 통계용 카운트 메서드
    public long getTotalCount()    { return hostRepository.count(); }
    public long getActiveCount()   { return hostRepository.countByAgentActiveTrue(); }
    public long getInactiveCount() { return hostRepository.count() - hostRepository.countByAgentActiveTrue(); }

    // 호스트 추가 — 저장 후 Default Group에 자동으로 등록
    public HostEntity addHost(String hostname, String ipAddress, String description) {
        HostEntity host = new HostEntity();
        host.setHostname(hostname);
        host.setIpAddress(ipAddress);
        host.setDescription(description);
        host.setEnabled(true);
        host.setAgentActive(false); // 에이전트 연결 전이므로 비활성
        HostEntity saved = hostRepository.save(host);

        // Default Group이 존재하고 아직 등록 안 됐으면 자동 등록
        hostGroupRepository.findByName("Default Group").ifPresent(defaultGroup -> {
            if (!hostGroupMemberRepository.existsByGroupIdAndHostId(defaultGroup.getId(), saved.getId())) {
                HostGroupMemberEntity member = new HostGroupMemberEntity();
                member.setGroupId(defaultGroup.getId());
                member.setHostId(saved.getId());
                hostGroupMemberRepository.save(member);
            }
        });

        return saved;
    }

    // 호스트 엔티티 직접 저장 (에이전트 상태 업데이트 등에 사용)
    public HostEntity saveHost(HostEntity host) {
        return hostRepository.save(host);
    }

    // 호스트 삭제 — 그룹 배정 정보도 함께 삭제
    public void deleteHost(Long id) {
        hostRepository.findById(id).ifPresent(host -> {
            hostGroupMemberRepository.deleteByHostId(id); // 그룹 매핑 먼저 삭제
            hostRepository.deleteById(id);
        });
    }
}
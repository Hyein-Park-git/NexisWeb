package com.nexis.web.service;

import com.nexis.web.entity.HostGroupMemberEntity;
import com.nexis.web.entity.ItemEntity;
import com.nexis.web.repository.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

@Component
public class HostSyncScheduler {

    private static final Logger logger = Logger.getLogger("NexisWebLogger");

    // 에이전트 전송 주기와 아이템 수집 주기가 완전히 일치하지 않을 수 있으므로
    // Inactive 판단 기준에 허용 오차를 적용 (50%)
    // 예: interval=60초 → 60 * 2 * 1.5 = 180초 내 미수신 시 Inactive
    private static final double TOLERANCE_FACTOR = 1.5;

    // 아이템이 없거나 interval이 0인 경우 사용할 기본 수집 주기 (초)
    private static final int DEFAULT_INTERVAL_SECONDS = 60;

    private final HostRepository            hostRepository;
    private final ItemRepository            itemRepository;
    private final InstallationService       installationService;
    private final TriggerService            triggerService;
    private final HostGroupRepository       hostGroupRepository;
    private final HostGroupMemberRepository hostGroupMemberRepository;

    public HostSyncScheduler(HostRepository            hostRepository,
                             ItemRepository            itemRepository,
                             InstallationService       installationService,
                             TriggerService            triggerService,
                             HostGroupRepository       hostGroupRepository,
                             HostGroupMemberRepository hostGroupMemberRepository) {
        this.hostRepository            = hostRepository;
        this.itemRepository            = itemRepository;
        this.installationService       = installationService;
        this.triggerService            = triggerService;
        this.hostGroupRepository       = hostGroupRepository;
        this.hostGroupMemberRepository = hostGroupMemberRepository;
    }

    // 30초마다 호스트 Active/Inactive 상태 갱신
    // 판단 기준: 해당 호스트의 활성 아이템 중 가장 짧은 interval 기준으로
    //            interval * 2 * TOLERANCE_FACTOR 초 내 미수신 시 Inactive 처리
    // TOLERANCE_FACTOR를 두는 이유: 에이전트 전송 주기와 아이템 수집 주기가
    //   정확히 일치하지 않거나 네트워크 지연이 있을 수 있기 때문
    @Scheduled(fixedDelay = 30_000)
    public void syncHostsFromItemData() {
        if (!installationService.isInstalled()) return;

        try {
            hostRepository.findAllByOrderByHostnameAsc().forEach(host -> {

                // 활성화된 아이템 중 가장 짧은 interval 선택
                // → 가장 빠른 주기 기준으로 체크해야 누락을 빨리 감지할 수 있음
                List<ItemEntity> items = itemRepository.findByHostIdOrderByCreatedAtAsc(host.getId());
                int minInterval = items.stream()
                    .filter(ItemEntity::isEnabled)
                    .mapToInt(ItemEntity::getInterval)
                    .filter(i -> i > 0)
                    .min()
                    .orElse(DEFAULT_INTERVAL_SECONDS); // 아이템 없거나 전부 0이면 기본값 사용

                // Inactive 판단 기준 시각 계산
                // interval=30s  → 30 * 2 * 1.5 = 90초 내 미수신 시 Inactive
                // interval=60s  → 60 * 2 * 1.5 = 180초 내 미수신 시 Inactive
                // interval=300s → 300 * 2 * 1.5 = 900초 내 미수신 시 Inactive
                long thresholdSeconds = (long) (minInterval * 2 * TOLERANCE_FACTOR);
                LocalDateTime cutoff  = LocalDateTime.now().minusSeconds(thresholdSeconds);

                boolean isRecent = host.getAgentLastCheck() != null
                    && host.getAgentLastCheck().isAfter(cutoff);

                // 기준 시각 이후 수신 기록이 없고 현재 active 상태면 → Inactive로 변경
                if (!isRecent && Boolean.TRUE.equals(host.isAgentActive())) {
                    host.setAgentActive(false);
                    hostRepository.save(host);
                    logger.info("[HostSync] " + host.getHostname()
                        + " → Inactive (interval=" + minInterval
                        + "s, threshold=" + thresholdSeconds + "s)");
                }
            });
        } catch (Exception e) {
            logger.warning("[HostSync] Sync failed: " + e.getMessage());
        }
    }

    // 30초마다 활성 트리거 조건 평가 → Problem 생성/해소
    @Scheduled(fixedDelay = 30_000)
    public void evaluateTriggers() {
        if (!installationService.isInstalled()) return;
        try {
            triggerService.evaluateAll();
        } catch (Exception e) {
            logger.warning("[TriggerEval] Failed: " + e.getMessage());
        }
    }

    // 30초마다 Default Group 미등록 호스트를 Default Group에 자동 등록
    // defaultGroupRegistered 플래그로 이미 처리된 호스트는 스킵
    @Scheduled(fixedDelay = 30_000)
    public void syncDefaultGroupRegistration() {
        if (!installationService.isInstalled()) return;
        try {
            hostGroupRepository.findByName("Default Group").ifPresent(defaultGroup -> {
                hostRepository.findAllByOrderByHostnameAsc().stream()
                    .filter(h -> !Boolean.TRUE.equals(h.isDefaultGroupRegistered()))
                    .forEach(host -> {
                        if (!hostGroupMemberRepository.existsByGroupIdAndHostId(
                                defaultGroup.getId(), host.getId())) {
                            HostGroupMemberEntity member = new HostGroupMemberEntity();
                            member.setGroupId(defaultGroup.getId());
                            member.setHostId(host.getId());
                            hostGroupMemberRepository.save(member);
                            logger.info("[DefaultGroup] Added " + host.getHostname() + " to Default Group");
                        }
                        // 등록 완료 플래그 세팅 → 다음 사이클에서 스킵됨
                        host.setDefaultGroupRegistered(true);
                        hostRepository.save(host);
                    });
            });
        } catch (Exception e) {
            logger.warning("[DefaultGroup] Sync failed: " + e.getMessage());
        }
    }
}
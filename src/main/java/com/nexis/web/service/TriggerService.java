package com.nexis.web.service;

import com.nexis.web.entity.*;
import com.nexis.web.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class TriggerService {

    private final TriggerRepository  triggerRepository;
    private final ProblemRepository  problemRepository;
    private final ItemRepository     itemRepository;
    private final ItemDataRepository itemDataRepository;
    private final HostRepository     hostRepository;

    public TriggerService(TriggerRepository  triggerRepository,
                          ProblemRepository  problemRepository,
                          ItemRepository     itemRepository,
                          ItemDataRepository itemDataRepository,
                          HostRepository     hostRepository) {
        this.triggerRepository  = triggerRepository;
        this.problemRepository  = problemRepository;
        this.itemRepository     = itemRepository;
        this.itemDataRepository = itemDataRepository;
        this.hostRepository     = hostRepository;
    }

    public List<TriggerEntity> getByHost(Long hostId)         { return triggerRepository.findByHostIdOrderByCreatedAtAsc(hostId); }
    public List<TriggerEntity> getByItem(Long itemId)         { return triggerRepository.findByItemIdOrderByCreatedAtAsc(itemId); }
    public Optional<TriggerEntity> getById(Long id)           { return triggerRepository.findById(id); }
    public TriggerEntity save(TriggerEntity t)                 { return triggerRepository.save(t); }
    public void delete(Long id)                                { triggerRepository.deleteById(id); }
    public List<TriggerEntity> getByTemplate(Long templateId) { return triggerRepository.findByTemplateIdOrderByCreatedAtAsc(templateId); }

    // 활성 Problem = PROBLEM + ACKNOWLEDGED 상태 (최신순 정렬)
    public List<ProblemEntity> getActiveProblems() {
        List<ProblemEntity> problems = problemRepository.findByStatusOrderByStartedAtDesc("PROBLEM");
        List<ProblemEntity> acked   = problemRepository.findByStatusOrderByStartedAtDesc("ACKNOWLEDGED");
        problems.addAll(acked);
        problems.sort((a, b) -> b.getStartedAt().compareTo(a.getStartedAt()));
        return problems;
    }

    public List<ProblemEntity> getAllProblems()    { return problemRepository.findAllByOrderByStartedAtDesc(); }
    public List<ProblemEntity> getRecentProblems() { return problemRepository.findTop100ByOrderByStartedAtDesc(); }

    // 활성 Problem 수 = PROBLEM + ACKNOWLEDGED 합산
    public long countActiveProblems() {
        return problemRepository.countByStatus("PROBLEM")
             + problemRepository.countByStatus("ACKNOWLEDGED");
    }

    public long countBySeverity(String severity) {
        return problemRepository.countByStatusAndSeverity("PROBLEM", severity)
             + problemRepository.countByStatusAndSeverity("ACKNOWLEDGED", severity);
    }

    // 활성화된 모든 트리거를 순회하며 조건 평가
    public void evaluateAll() {
        List<TriggerEntity> triggers = triggerRepository.findByEnabledTrueOrderByCreatedAtAsc();
        for (TriggerEntity trigger : triggers) {
            try {
                evaluate(trigger);
            } catch (Exception ignored) {} // 개별 트리거 실패가 전체 평가를 막지 않도록
        }
    }

    // 트리거 조건 평가 핵심 로직
    private void evaluate(TriggerEntity trigger) {
        ItemEntity item = itemRepository.findById(trigger.getItemId()).orElse(null);
        if (item == null || item.getHostId() == null) return; // 템플릿 원본 트리거는 스킵

        String hostname = hostRepository.findById(item.getHostId())
            .map(HostEntity::getHostname)
            .orElse(null);
        if (hostname == null) return;

        String func     = trigger.getFunc();
        int    duration = trigger.getDuration() != null ? trigger.getDuration() : 0;

        // duration이 있으면 그 분 동안의 데이터를, 없으면 함수별 기본 룩백 시간 사용
        int lookbackMinutes = duration > 0 ? duration : switch (func) {
            case "avg", "min", "max" -> 5;
            default -> 2; // last
        };

        LocalDateTime from = LocalDateTime.now().minusMinutes(lookbackMinutes);
        List<ItemDataEntity> dataList = itemDataRepository
            .findByItemIdAndCollectedAtAfterOrderByCollectedAtAsc(trigger.getItemId(), from);

        if (dataList.isEmpty()) return;

        List<Double> vals = dataList.stream()
            .map(ItemDataEntity::getValue)
            .filter(v -> v != null)
            .toList();

        if (vals.isEmpty()) return;

        boolean fired;
        if (duration > 0) {
            // duration 모드: 지속 시간 동안 모든 값이 조건을 만족해야 발동
            // 데이터가 2건 미만이면 지속 판단 불가 → 미발동
            if (vals.size() < 2) {
                fired = false;
            } else {
                fired = vals.stream()
                    .allMatch(v -> evaluateCondition(v, trigger.getOperator(), trigger.getThreshold()));
            }
        } else {
            // 일반 모드: 집계 함수(avg/min/max/last) 결과를 단일 비교
            double computed = switch (func) {
                case "avg" -> vals.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                case "min" -> vals.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                case "max" -> vals.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                default    -> vals.get(vals.size() - 1); // last: 가장 최근 값
            };
            fired = evaluateCondition(computed, trigger.getOperator(), trigger.getThreshold());
        }

        // 현재 활성 Problem 조회 (PROBLEM, ACKNOWLEDGED 각각)
        Optional<ProblemEntity> existingProblem = problemRepository.findByTriggerIdAndStatus(trigger.getId(), "PROBLEM");
        Optional<ProblemEntity> existingAcked   = problemRepository.findByTriggerIdAndStatus(trigger.getId(), "ACKNOWLEDGED");
        Optional<ProblemEntity> existing        = existingProblem.isPresent() ? existingProblem : existingAcked;

        // 뷰에 표시할 값 문자열 계산
        double displayVal = duration > 0
            ? vals.stream().mapToDouble(Double::doubleValue).average().orElse(0)
            : switch (func) {
                case "avg" -> vals.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                case "min" -> vals.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                case "max" -> vals.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                default    -> vals.get(vals.size() - 1);
            };
        String displayValue = String.format("%.2f%s", displayVal,
            item.getUnitDisplay() != null ? " " + item.getUnitDisplay() : "");

        if (fired) {
            if (existing.isEmpty()) {
                // 새로 조건 충족 → Problem 생성
                saveProblem(trigger, item, hostname, displayValue);
            } else {
                // 이미 활성 Problem 존재 → 현재 값만 업데이트
                ProblemEntity p = existing.get();
                p.setValue(displayValue);
                problemRepository.save(p);
            }
        } else {
            // 조건 미충족 → PROBLEM 상태만 자동 RESOLVED 처리
            // ACKNOWLEDGED는 수동 확인 상태이므로 자동으로 해소하지 않음
            if (existingProblem.isPresent()) {
                ProblemEntity p = existingProblem.get();
                p.setStatus("RESOLVED");
                p.setResolvedAt(LocalDateTime.now());
                problemRepository.save(p);
            }
        }
    }

    // 새 Problem 엔티티 생성 및 저장
    private void saveProblem(TriggerEntity trigger, ItemEntity item,
                             String hostname, String value) {
        ProblemEntity p = new ProblemEntity();
        p.setTriggerId(trigger.getId());
        p.setHostId(item.getHostId());
        p.setHostname(hostname);
        p.setTriggerName(trigger.getName());
        p.setItemName(item.getName());
        p.setExpression(trigger.getExpression());
        p.setSeverity(trigger.getSeverity());
        p.setValue(value);
        p.setStatus("PROBLEM");
        problemRepository.save(p);
    }

    // 조건 연산자 평가 헬퍼
    private boolean evaluateCondition(double val, String op, double threshold) {
        return switch (op) {
            case ">"  -> val >  threshold;
            case ">=" -> val >= threshold;
            case "<"  -> val <  threshold;
            case "<=" -> val <= threshold;
            case "="  -> val == threshold;
            default   -> false;
        };
    }
}
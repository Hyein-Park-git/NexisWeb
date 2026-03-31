package com.nexis.web.controller;

import com.nexis.web.entity.*;
import com.nexis.web.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping
public class UserManageController {

    private final UserRepository                userRepository;
    private final UserGroupRepository           userGroupRepository;
    private final UserGroupMemberRepository     userGroupMemberRepository;
    private final UserGroupPermissionRepository permissionRepository;
    private final UserGroupHostAccessRepository hostAccessRepository;
    private final HostGroupRepository           hostGroupRepository;
    private final PasswordEncoder               passwordEncoder;
    private final UserDetailsService            userDetailsService;

    public UserManageController(UserRepository                userRepository,
                                UserGroupRepository           userGroupRepository,
                                UserGroupMemberRepository     userGroupMemberRepository,
                                UserGroupPermissionRepository permissionRepository,
                                UserGroupHostAccessRepository hostAccessRepository,
                                HostGroupRepository           hostGroupRepository,
                                PasswordEncoder               passwordEncoder,
                                UserDetailsService            userDetailsService) {
        this.userRepository            = userRepository;
        this.userGroupRepository       = userGroupRepository;
        this.userGroupMemberRepository = userGroupMemberRepository;
        this.permissionRepository      = permissionRepository;
        this.hostAccessRepository      = hostAccessRepository;
        this.hostGroupRepository       = hostGroupRepository;
        this.passwordEncoder           = passwordEncoder;
        this.userDetailsService        = userDetailsService;
    }

    /* ════════════════════════════════
       페이지 컨트롤러
    ════════════════════════════════ */

    // 유저 목록 페이지
    @GetMapping("/administration/users")
    public String usersPage(Model model) {
        model.addAttribute("users",      userRepository.findAllByOrderByUsernameAsc());
        model.addAttribute("pageTitle",  "Users");
        model.addAttribute("viewName",   "administration/users");
        model.addAttribute("activeMenu", "users");
        return "layout/main";
    }

    // 유저 추가 폼 페이지
    @GetMapping("/administration/users/new")
    public String newUserPage(Model model) {
        model.addAttribute("pageTitle",  "Add User");
        model.addAttribute("viewName",   "administration/user-new");
        model.addAttribute("activeMenu", "users");
        return "layout/main";
    }

    // 유저 수정 폼 페이지 — 현재 소속 그룹 목록도 함께 전달
    @GetMapping("/administration/users/{id}/edit")
    public String editUserPage(@PathVariable("id") Long id, Model model) {
        return userRepository.findById(id).map(user -> {
            List<Long> userGroupIds = userGroupMemberRepository.findByUserId(id).stream()
                .map(UserGroupMemberEntity::getGroupId)
                .collect(Collectors.toList());
            model.addAttribute("editUser",     user);
            model.addAttribute("userGroupIds", userGroupIds);  // 현재 소속 그룹 ID (체크박스 선택 상태용)
            model.addAttribute("groups",       userGroupRepository.findAllByOrderByNameAsc());
            model.addAttribute("pageTitle",    "Edit User");
            model.addAttribute("viewName",     "administration/user-edit");
            model.addAttribute("activeMenu",   "users");
            return "layout/main";
        }).orElse("redirect:/administration/users");
    }

    // 유저 그룹 목록 페이지 — 각 그룹의 멤버 수도 계산
    @GetMapping("/administration/user-groups")
    public String userGroupsPage(Model model) {
        List<UserGroupEntity> groups = userGroupRepository.findAllByOrderByNameAsc();
        Map<Long, Long> memberCountMap = new HashMap<>();
        for (UserGroupEntity g : groups) {
            memberCountMap.put(g.getId(), (long) userGroupMemberRepository.findByGroupId(g.getId()).size());
        }
        model.addAttribute("userGroups",     groups);
        model.addAttribute("memberCountMap", memberCountMap);
        model.addAttribute("hostGroups",     hostGroupRepository.findAllByOrderByNameAsc()); // 권한 설정 모달용
        model.addAttribute("pageTitle",      "User Groups");
        model.addAttribute("viewName",       "administration/user-groups");
        model.addAttribute("activeMenu",     "usergroups");
        return "layout/main";
    }

    /* ════════════════════════════════
       사용자 CRUD API
    ════════════════════════════════ */

    // 유저 추가 API — 그룹 배정도 함께 처리
    @PostMapping("/api/users/add")
    @ResponseBody
    public ResponseEntity<?> addUser(@RequestBody Map<String, Object> body) {
        try {
            String username = (String) body.get("username");
            if (username == null || username.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "Username is required."));
            if (userRepository.existsByUsername(username.trim()))
                return ResponseEntity.badRequest().body(Map.of("error", "Username already exists."));
            String rawPassword = (String) body.getOrDefault("password", "");
            if (rawPassword.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "Password is required."));
            UserEntity user = new UserEntity();
            user.setUsername(username.trim());
            user.setFullName((String) body.getOrDefault("fullName", ""));
            user.setDescription((String) body.getOrDefault("description", ""));
            user.setPassword(passwordEncoder.encode(rawPassword)); // 비밀번호 BCrypt 암호화
            user.setRole((String) body.getOrDefault("role", "User"));
            user.setEnabled(true);
            userRepository.save(user);
            // 요청에 그룹 ID가 있으면 그룹 배정
            List<?> groupIds = (List<?>) body.get("groupIds");
            if (groupIds != null) {
                for (Object gid : groupIds) {
                    Long groupId = ((Number) gid).longValue();
                    if (!userGroupRepository.existsById(groupId)) continue;
                    if (userGroupMemberRepository.existsByUserIdAndGroupId(user.getId(), groupId)) continue;
                    UserGroupMemberEntity m = new UserGroupMemberEntity();
                    m.setUserId(user.getId());
                    m.setGroupId(groupId);
                    userGroupMemberRepository.save(m);
                }
            }
            return ResponseEntity.ok(Map.of("success", true, "id", user.getId()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 유저 정보 수정 API — groupIds가 있으면 기존 배정 초기화 후 재배정
    @PutMapping("/api/users/{id}")
    @ResponseBody
    public ResponseEntity<?> updateUser(@PathVariable("id") Long id,
                                        @RequestBody Map<String, Object> body) {
        try {
            return userRepository.findById(id).map(user -> {
                if (body.containsKey("fullName"))    user.setFullName((String) body.get("fullName"));
                if (body.containsKey("description")) user.setDescription((String) body.get("description"));
                if (body.containsKey("role"))        user.setRole((String) body.get("role"));
                if (body.containsKey("enabled"))     user.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
                if (body.containsKey("password")) {
                    String pw = (String) body.get("password");
                    if (pw != null && !pw.isBlank()) user.setPassword(passwordEncoder.encode(pw));
                }
                userRepository.save(user);
                if (body.containsKey("groupIds")) {
                    userGroupMemberRepository.deleteByUserId(id); // 기존 그룹 배정 초기화
                    List<?> groupIds = (List<?>) body.get("groupIds");
                    if (groupIds != null) {
                        for (Object gid : groupIds) {
                            Long groupId = ((Number) gid).longValue();
                            if (!userGroupRepository.existsById(groupId)) continue;
                            UserGroupMemberEntity m = new UserGroupMemberEntity();
                            m.setUserId(id);
                            m.setGroupId(groupId);
                            userGroupMemberRepository.save(m);
                        }
                    }
                }
                return ResponseEntity.ok(Map.<String,Object>of("success", true));
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 유저 삭제 API — 그룹 배정도 함께 삭제
    @DeleteMapping("/api/users/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteUser(@PathVariable("id") Long id) {
        try {
            if (!userRepository.existsById(id)) return ResponseEntity.notFound().build();
            userGroupMemberRepository.deleteByUserId(id); // 그룹 배정 먼저 삭제
            userRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 유저 활성화/비활성화 토글 API
    @PutMapping("/api/users/{id}/toggle")
    @ResponseBody
    public ResponseEntity<?> toggleUser(@PathVariable("id") Long id) {
        try {
            return userRepository.findById(id).map(user -> {
                user.setEnabled(!user.isEnabled());
                userRepository.save(user);
                return ResponseEntity.ok(Map.of("success", true, "enabled", user.isEnabled()));
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /* ════════════════════════════════
       유저 그룹 CRUD API
    ════════════════════════════════ */

    // 유저 그룹 전체 목록 조회
    @GetMapping("/api/user-groups")
    @ResponseBody
    public ResponseEntity<?> getUserGroups() {
        try {
            List<Map<String, Object>> result = userGroupRepository.findAllByOrderByNameAsc().stream()
                .map(g -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id",          g.getId());
                    m.put("name",        g.getName());
                    m.put("description", g.getDescription() != null ? g.getDescription() : "");
                    m.put("enabled",     g.isEnabled());
                    return m;
                }).collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 유저 그룹 추가 — 생성 시 모든 리소스 권한을 none으로 초기화
    @PostMapping("/api/user-groups/add")
    @ResponseBody
    public ResponseEntity<?> addUserGroup(@RequestBody Map<String, String> body) {
        try {
            String name = body.get("name");
            if (name == null || name.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "Group name is required."));
            if (userGroupRepository.existsByName(name.trim()))
                return ResponseEntity.badRequest().body(Map.of("error", "Group name already exists."));
            UserGroupEntity group = new UserGroupEntity();
            group.setName(name.trim());
            group.setDescription(body.getOrDefault("description", ""));
            group.setEnabled(true);
            userGroupRepository.save(group);

            // 모든 리소스에 대해 기본 권한 none으로 초기화
            for (String resource : new String[]{"dashboard", "monitoring", "configuration", "administration"}) {
                UserGroupPermissionEntity p = new UserGroupPermissionEntity();
                p.setGroupId(group.getId());
                p.setResource(resource);
                p.setAccessLevel("none");
                permissionRepository.save(p);
            }

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 유저 그룹 이름/설명 수정
    @PutMapping("/api/user-groups/{id}")
    @ResponseBody
    public ResponseEntity<?> updateUserGroup(@PathVariable("id") Long id,
                                             @RequestBody Map<String, String> body) {
        try {
            return userGroupRepository.findById(id).map(g -> {
                if (body.containsKey("name") && !body.get("name").isBlank()) {
                    String newName = body.get("name").trim();
                    if (!newName.equals(g.getName()) && userGroupRepository.existsByName(newName))
                        return ResponseEntity.badRequest().body(Map.<String,Object>of("error", "Group name already exists."));
                    g.setName(newName);
                }
                if (body.containsKey("description")) g.setDescription(body.get("description"));
                userGroupRepository.save(g);
                return ResponseEntity.ok(Map.<String,Object>of("success", true));
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 유저 그룹 삭제 — 멤버/권한/호스트 접근 설정까지 모두 삭제 (@Transactional)
    @Transactional
    @DeleteMapping("/api/user-groups/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteUserGroup(@PathVariable("id") Long id) {
        try {
            if (!userGroupRepository.existsById(id)) return ResponseEntity.notFound().build();
            userGroupMemberRepository.deleteByGroupId(id);
            permissionRepository.deleteByGroupId(id);
            hostAccessRepository.deleteByGroupId(id);
            userGroupRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 유저 그룹 멤버 목록 조회 — userId로 유저 정보 join
    @GetMapping("/api/user-groups/{id}/members")
    @ResponseBody
    public ResponseEntity<?> getUserGroupMembers(@PathVariable("id") Long id) {
        try {
            List<Map<String, Object>> members = userGroupMemberRepository.findByGroupId(id).stream()
                .map(m -> {
                    var user = userRepository.findById(m.getUserId()).orElse(null);
                    Map<String, Object> map = new HashMap<>();
                    map.put("id",       m.getId());
                    map.put("userId",   m.getUserId());
                    map.put("username", user != null ? user.getUsername() : "—");
                    map.put("fullName", user != null && user.getFullName() != null ? user.getFullName() : "—");
                    map.put("role",     user != null ? user.getRole() : "—");
                    map.put("enabled",  user != null && user.isEnabled());
                    return map;
                }).collect(Collectors.toList());
            return ResponseEntity.ok(members);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 유저 그룹에 멤버 추가 (여러 명 한 번에, 이미 있으면 스킵)
    @PostMapping("/api/user-groups/{id}/members")
    @ResponseBody
    public ResponseEntity<?> addUserGroupMember(@PathVariable("id") Long id,
                                                @RequestBody Map<String, Object> body) {
        try {
            if (!userGroupRepository.existsById(id)) return ResponseEntity.notFound().build();
            List<?> userIds = (List<?>) body.get("userIds");
            if (userIds == null || userIds.isEmpty())
                return ResponseEntity.badRequest().body(Map.of("error", "userIds is required."));
            int added = 0;
            for (Object uid : userIds) {
                Long userId = ((Number) uid).longValue();
                if (!userRepository.existsById(userId)) continue;
                if (userGroupMemberRepository.existsByUserIdAndGroupId(userId, id)) continue;
                UserGroupMemberEntity m = new UserGroupMemberEntity();
                m.setUserId(userId);
                m.setGroupId(id);
                userGroupMemberRepository.save(m);
                added++;
            }
            return ResponseEntity.ok(Map.of("success", true, "added", added));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 유저 그룹에서 멤버 단건 제거
    @Transactional
    @DeleteMapping("/api/user-groups/{id}/members/{memberId}")
    @ResponseBody
    public ResponseEntity<?> removeUserGroupMember(@PathVariable("id") Long id,
                                                   @PathVariable("memberId") Long memberId) {
        try {
            userGroupMemberRepository.deleteById(memberId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 유저 그룹 멤버 추가 모달용 — 전체 유저 목록 반환
    @GetMapping("/api/user-groups-users")
    @ResponseBody
    public ResponseEntity<?> availableUsers() {
        try {
            List<Map<String, Object>> result = userRepository.findAllByOrderByUsernameAsc().stream()
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id",       u.getId());
                    m.put("username", u.getUsername());
                    m.put("fullName", u.getFullName() != null ? u.getFullName() : "");
                    m.put("role",     u.getRole());
                    m.put("enabled",  u.isEnabled());
                    return m;
                }).collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /* ════════════════════════════════
       권한 API
    ════════════════════════════════ */

    // 유저 그룹 권한 + 허용 호스트 그룹 조회
    @GetMapping("/api/user-groups/{id}/permissions")
    @ResponseBody
    public ResponseEntity<?> getGroupPermissions(@PathVariable("id") Long id) {
        try {
            var perms      = permissionRepository.findByGroupId(id);
            var hostAccess = hostAccessRepository.findByGroupId(id);
            Map<String, String> permMap = new HashMap<>();
            for (var p : perms) permMap.put(p.getResource(), p.getAccessLevel());
            Map<String, Object> result = new HashMap<>();
            result.put("permissions",  permMap);
            result.put("hostGroupIds", hostAccess.stream()
                .map(UserGroupHostAccessEntity::getHostGroupId)
                .collect(Collectors.toList()));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 유저 그룹 권한 저장 — 기존 권한을 전부 지우고 새로 저장 (replace 방식)
    @Transactional
    @PostMapping("/api/user-groups/{id}/permissions")
    @ResponseBody
    public ResponseEntity<?> saveGroupPermissions(@PathVariable("id") Long id,
                                                  @RequestBody Map<String, Object> body) {
        try {
            permissionRepository.deleteByGroupId(id);   // 기존 권한 초기화
            hostAccessRepository.deleteByGroupId(id);   // 기존 호스트 접근 초기화

            // 권한 저장
            Map<?,?> perms = (Map<?,?>) body.get("permissions");
            if (perms != null) {
                for (Map.Entry<?,?> e : perms.entrySet()) {
                    UserGroupPermissionEntity p = new UserGroupPermissionEntity();
                    p.setGroupId(id);
                    p.setResource(e.getKey().toString());
                    p.setAccessLevel(e.getValue().toString());
                    permissionRepository.save(p);
                }
            }

            // 호스트 그룹 접근 저장 — Default Group은 항상 포함
            List<Long> hgIds = new ArrayList<>();
            List<?> bodyHgIds = (List<?>) body.get("hostGroupIds");
            if (bodyHgIds != null) {
                for (Object hid : bodyHgIds) {
                    hgIds.add(((Number) hid).longValue());
                }
            }
            // Default Group이 목록에 없으면 자동으로 추가
            hostGroupRepository.findByName("Default Group").ifPresent(defaultGroup -> {
                if (!hgIds.contains(defaultGroup.getId())) {
                    hgIds.add(defaultGroup.getId());
                }
            });
            for (Long hgId : hgIds) {
                UserGroupHostAccessEntity a = new UserGroupHostAccessEntity();
                a.setGroupId(id);
                a.setHostGroupId(hgId);
                hostAccessRepository.save(a);
            }
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /* ════════════════════════════════
       비밀번호 변경
    ════════════════════════════════ */

    // 비밀번호 변경 API — 현재 비밀번호 검증 후 새 비밀번호로 교체
    @PostMapping("/api/users/change-password")
    @ResponseBody
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body) {
        try {
            String username        = body.get("username");
            String currentPassword = body.get("currentPassword");
            String newPassword     = body.get("newPassword");
            if (username == null || currentPassword == null || newPassword == null)
                return ResponseEntity.badRequest().body(Map.of("error", "All fields are required."));
            if (newPassword.length() < 4)
                return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 4 characters."));
            // Spring Security의 UserDetailsService로 현재 유저 조회
            org.springframework.security.core.userdetails.UserDetails details;
            try { details = userDetailsService.loadUserByUsername(username); }
            catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("error", "User not found.")); }
            // passwordEncoder.matches(): 입력값과 저장된 해시값 비교
            if (!passwordEncoder.matches(currentPassword, details.getPassword()))
                return ResponseEntity.badRequest().body(Map.of("error", "Current password is incorrect."));
            UserEntity user = userRepository.findByUsername(username).orElse(null);
            if (user == null)
                return ResponseEntity.badRequest().body(Map.of("error", "User not found in database."));
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 개발/디버그용 — 현재 로그인 유저의 인증 정보 확인
    @GetMapping("/debug/auth")
    @ResponseBody
    public Object debugAuth() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                       .getContext().getAuthentication();
        return java.util.Map.of(
            "name",          auth.getName(),
            "authorities",   auth.getAuthorities().toString(),
            "authenticated", auth.isAuthenticated()
        );
    }
}
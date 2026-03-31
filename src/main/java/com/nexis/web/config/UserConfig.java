package com.nexis.web.config;

import com.nexis.web.repository.UserRepository;
import com.nexis.web.service.InstallationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Configuration
public class UserConfig {

    // PasswordEncoder: 비밀번호를 단방향 해시로 암호화하는 인터페이스
    // BCrypt는 솔트(salt)를 자동으로 붙여서 같은 비밀번호도 매번 다른 해시값을 만듦 → 레인보우 테이블 공격 방어
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // UserDetailsService: Spring Security가 로그인 시 사용자 정보를 가져오기 위해 호출하는 인터페이스
    // loadUserByUsername(username)을 구현해야 함
    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository,
                                                 InstallationService installationService) {
        return username -> {

            // 설치 전에는 DB가 없으므로 하드코딩된 임시 Admin 계정으로 fallback
            if (!installationService.isInstalled()) {
                if ("Admin".equals(username)) {
                    return new User(
                        "Admin",
                        new BCryptPasswordEncoder().encode("nexis"), // 임시 비밀번호
                        true, true, true, true,                      // enabled, accountNonExpired, credentialsNonExpired, accountNonLocked
                        List.of(
                            new SimpleGrantedAuthority("Administrator"),
                            new SimpleGrantedAuthority("ROLE_Administrator") // Spring Security 관례상 ROLE_ 접두사도 함께 등록
                        )
                    );
                }
                throw new UsernameNotFoundException("User not found: " + username);
            }

            // 설치 후 → DB에서 유저를 조회해 Spring Security의 UserDetails 객체로 변환
            return userRepository.findByUsername(username)
                .map(u -> {
                    String role = u.getRole() != null ? u.getRole() : "User"; // role이 null이면 기본값 "User"
                    return new User(
                        u.getUsername(),
                        u.getPassword(), // DB에 이미 BCrypt로 저장된 값 그대로 사용
                        u.isEnabled(),
                        true, true, true,
                        List.of(
                            new SimpleGrantedAuthority(role),
                            new SimpleGrantedAuthority("ROLE_" + role)
                        )
                    );
                })
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        };
    }
}
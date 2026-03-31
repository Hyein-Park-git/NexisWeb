package com.nexis.web.config;

import com.nexis.web.service.InstallationService;
import com.nexis.web.service.PermissionService;
import com.nexis.web.interceptor.PermissionInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// @EnableWebSecurity: Spring Security 활성화. 이게 있어야 아래 설정이 적용됨
// WebMvcConfigurer: MVC 설정(인터셉터 등)을 커스터마이징하기 위해 구현
@Configuration
@EnableWebSecurity
public class SecurityConfig implements WebMvcConfigurer {

    private final InstallationService installationService;
    private final PermissionService   permissionService;

    public SecurityConfig(InstallationService installationService,
                          PermissionService   permissionService) {
        this.installationService = installationService;
        this.permissionService   = permissionService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // 설치/정적 리소스/설치 API는 로그인 없이 접근 허용
                .requestMatchers(
                    "/", "/install/**", "/static/**",
                    "/css/**", "/js/**", "/images/**", "/api/install/**"
                ).permitAll()
                // /administration/** 은 로그인만 되어 있으면 접근 허용
                // 세부 권한(read/write/none)은 PermissionInterceptor에서 별도로 처리
                .requestMatchers("/administration/**").authenticated()
                // 그 외 모든 경로도 로그인 필요
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")                              // 커스텀 로그인 페이지 URL
                .loginProcessingUrl("/login")                     // 폼 POST를 처리할 URL
                .failureUrl("/login?error=true")                  // 로그인 실패 시 이동할 URL
                .defaultSuccessUrl("/monitoring/dashboard", true) // 로그인 성공 시 항상 이 URL로 이동
                .permitAll()
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout")) // GET /logout도 처리
                .logoutSuccessUrl("/login?logout=true")
                .permitAll()
            )
            .exceptionHandling(ex -> ex
                // 권한 없는 페이지 접근(403) 시 에러 페이지 대신 대시보드로 리다이렉트
                .accessDeniedHandler((req, res, e) ->
                    res.sendRedirect("/monitoring/dashboard?denied=true"))
            )
            // REST API(/api/**)는 CSRF 토큰 검증 면제
            // CSRF는 브라우저 폼 기반 공격 방어용이라 API에는 불필요
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            // iframe은 같은 출처(same-origin)에서만 허용
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // PermissionInterceptor: 컨트롤러 진입 전에 URL별 세부 권한을 체크
        // Security는 "로그인 여부"만 보고, 실제 read/write/none 권한은 여기서 처리
        registry.addInterceptor(new PermissionInterceptor(permissionService))
                .addPathPatterns("/**")
                .excludePathPatterns( // 정적 리소스, API, 인증 경로는 권한 체크 제외
                    "/css/**", "/js/**", "/images/**",
                    "/api/**", "/login", "/logout", "/install/**"
                );
    }
}
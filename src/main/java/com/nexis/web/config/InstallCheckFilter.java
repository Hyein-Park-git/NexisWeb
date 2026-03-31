package com.nexis.web.config;

import com.nexis.web.service.InstallationService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

// Filter: 모든 HTTP 요청이 컨트롤러에 도달하기 전에 가로채는 서블릿 필터
// @Order(1): 여러 필터 중 가장 먼저 실행되도록 우선순위 지정
@Component
@Order(1)
public class InstallCheckFilter implements Filter {

    private final InstallationService installationService;

    public InstallCheckFilter(InstallationService installationService) {
        this.installationService = installationService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  httpRequest  = (HttpServletRequest)  request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // 아래 경로들은 설치 여부와 무관하게 항상 통과시킴
        // - 설치 페이지 자체(/install), 정적 리소스, 로그인, API 전체
        // chain.doFilter() = "다음 필터 또는 컨트롤러로 넘겨줘"
        if (path.startsWith("/install")
                || path.startsWith("/static")
                || path.startsWith("/css")
                || path.startsWith("/js")
                || path.startsWith("/images")
                || path.equals("/login")
                || path.startsWith("/api/")
                || path.equals("/")) {
            chain.doFilter(request, response);
            return;
        }

        // 설치가 안 됐으면 설치 마법사 페이지로 강제 리다이렉트
        if (!installationService.isInstalled()) {
            httpResponse.sendRedirect("/install");
            return;
        }

        // 설치 완료 → 정상적으로 다음 단계로 통과
        chain.doFilter(request, response);
    }
}
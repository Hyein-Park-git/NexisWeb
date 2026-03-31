package com.nexis.web.interceptor;

import com.nexis.web.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

// HandlerInterceptor: 컨트롤러 실행 전/후/완료 시점에 끼어드는 인터셉터 인터페이스
// 여기서는 postHandle만 사용 — 컨트롤러가 뷰를 반환한 직후, 렌더링 전에 실행됨
public class PermissionInterceptor implements HandlerInterceptor {

    private final PermissionService permissionService;

    public PermissionInterceptor(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    // postHandle: 컨트롤러 실행 후 뷰 렌더링 전에 호출
    // 모든 뷰에 공통으로 권한 정보를 주입하기 위해 사용
    @Override
    public void postHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler,
                           ModelAndView modelAndView) {

        if (modelAndView == null) return;        // API 응답 등 뷰가 없는 경우 스킵
        String viewName = modelAndView.getViewName();
        if (viewName == null) return;

        // redirect/forward는 뷰 렌더링이 아니므로 스킵
        if (viewName.startsWith("redirect:") || viewName.startsWith("forward:")) return;

        // 모든 뷰에 administration 권한 정보를 자동으로 주입
        // → Thymeleaf에서 ${canReadAdmin}, ${canWriteAdmin}으로 메뉴 표시 여부 결정에 사용
        modelAndView.addObject("canReadAdmin",  permissionService.canRead("administration"));
        modelAndView.addObject("canWriteAdmin", permissionService.canWrite("administration"));
    }
}
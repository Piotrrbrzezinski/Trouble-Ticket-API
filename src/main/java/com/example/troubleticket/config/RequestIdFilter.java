package com.example.troubleticket.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
public class RequestIdFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id=UUID.randomUUID().toString();
        request.setAttribute("requestId",id);
        response.setHeader("X-Request-ID",id);
        chain.doFilter(request,response);
    }
}

package com.streamplatform.streamapi.security;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService customUserDetailsService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService customUserDetailsService) {

        this.jwtService = jwtService;
        this.customUserDetailsService = customUserDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader == null ||
                !authorizationHeader.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authorizationHeader.substring(7).trim();

        if (jwt.isEmpty()) {
            request.setAttribute("jwt_error", "JWT token is missing or empty");
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String username = jwtService.extractUsername(jwt);

            if (username != null &&
                    SecurityContextHolder.getContext().getAuthentication() == null) {

                CustomUserDetails userDetails = (CustomUserDetails) customUserDetailsService
                        .loadUserByUsername(username);

                if (jwtService.isTokenValid(jwt, userDetails.getUsername())) {

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities());

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request));

                    SecurityContextHolder.getContext()
                            .setAuthentication(authentication);
                } else {
                    request.setAttribute("jwt_error", "Invalid or expired JWT token");
                    SecurityContextHolder.clearContext();
                }
            }
        } catch (ExpiredJwtException ex) {
            request.setAttribute("jwt_error", "JWT token has expired");
            SecurityContextHolder.clearContext();
        } catch (MalformedJwtException ex) {
            request.setAttribute("jwt_error", "Malformed JWT token");
            SecurityContextHolder.clearContext();
        } catch (SignatureException ex) {
            request.setAttribute("jwt_error", "Invalid JWT signature");
            SecurityContextHolder.clearContext();
        } catch (UnsupportedJwtException ex) {
            request.setAttribute("jwt_error", "Unsupported JWT token");
            SecurityContextHolder.clearContext();
        } catch (JwtException | IllegalArgumentException ex) {
            request.setAttribute("jwt_error", "Invalid JWT token");
            SecurityContextHolder.clearContext();
        } catch (UsernameNotFoundException ex) {
            request.setAttribute("jwt_error", "User not found");
            SecurityContextHolder.clearContext();
        } catch (Exception ex) {
            request.setAttribute("jwt_error", "Authentication failed");
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
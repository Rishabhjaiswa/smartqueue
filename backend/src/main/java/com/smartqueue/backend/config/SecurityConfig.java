package com.smartqueue.backend.config;

import com.smartqueue.backend.service.StaffUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                "/api/patient/register",
                                "/api/patient/status/**",
                                "/telegram/webhook",
                                "/ws/**", "/ws/info",
                                "/api/reception/display",
                                "/actuator/health"         // Docker healthcheck — no auth needed
                        ).permitAll()
                        .requestMatchers("/api/reception/**")
                        .hasAnyRole("RECEPTIONIST", "ADMIN")

                        .requestMatchers("/api/doctor/**")
                        .hasAnyRole("DOCTOR", "ADMIN")

                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")

                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler((req, res, auth) -> {
                            var session = req.getSession(true);
                            org.springframework.security.core.context.SecurityContext context =
                                    SecurityContextHolder.createEmptyContext();

                            context.setAuthentication(auth);
                            session.setAttribute(
                                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                                    context
                            );

                            SecurityContextHolder.setContext(context);
                            res.setStatus(200);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"role\":\"" +
                                    auth.getAuthorities().iterator()
                                            .next().getAuthority() + "\"}");
                        })
                        .failureHandler((req, res, ex) -> {
                            res.setStatus(401);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"message\":\"Invalid credentials\"}");
                        })
                        .permitAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, authException) -> {
                            res.setStatus(HttpStatus.UNAUTHORIZED.value());
                            res.setContentType("application/json");
                            res.getWriter().write("{\"message\":\"Unauthorized\"}");
                        })
                )
                .logout(l -> l.logoutUrl("/api/auth/logout").permitAll());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            HttpSecurity http,
            PasswordEncoder passwordEncoder,
            StaffUserService staffUserService) throws Exception {

        return http.getSharedObject(AuthenticationManagerBuilder.class)
                .userDetailsService(staffUserService)
                .passwordEncoder(passwordEncoder)
                .and()
                .build();
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();

        List<String> originList = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();

        config.setAllowedOrigins(originList.isEmpty() ? List.of("http://localhost:3000") : originList);
        config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(java.util.List.of("*"));
        config.setAllowCredentials(true);

        org.springframework.web.cors.UrlBasedCorsConfigurationSource source =
                new org.springframework.web.cors.UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

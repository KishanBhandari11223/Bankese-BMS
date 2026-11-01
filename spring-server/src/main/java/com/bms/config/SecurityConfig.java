
package com.bms.config;
import jakarta.servlet.http.HttpServletResponse;
import com.bms.security.JwtAuthFilter;
import com.bms.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
public class SecurityConfig {

        @Bean
                FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(
                        JwtAuthFilter jwtAuthFilter) {
                FilterRegistrationBean<JwtAuthFilter> registration =
                        new FilterRegistrationBean<>(jwtAuthFilter);
                registration.setEnabled(false);
                return registration;
        }
    @Bean
    UserDetailsService userDetailsService(
            UserRepository repository) {
        return username -> repository.findByUsernameIgnoreCase(username)
                .map(user -> User.withUsername(user.getUsername())
                        .password(user.getPasswordHash())
                        .roles(user.getRole().name())
                        .disabled(user.getStatus()
                                == com.bms.entity.User.Status.DISABLED)
                        .accountLocked(user.getStatus()
                                == com.bms.entity.User.Status.LOCKED)
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found"));
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthFilter jwtAuthFilter) throws Exception {

        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, ex) ->
                                response.sendError(
                                        HttpServletResponse.SC_UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/health",
                                "/api/v1/auth/register",
                                "/api/v1/auth/login").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}

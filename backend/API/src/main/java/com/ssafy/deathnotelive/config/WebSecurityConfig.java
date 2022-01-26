package com.ssafy.deathnotelive.config;

import com.ssafy.deathnotelive.config.jwt.JwtAuthenticationFilter;
import com.ssafy.deathnotelive.config.jwt.JwtAuthorizationFilter;
import com.ssafy.deathnotelive.config.jwt.JwtProperties;
import com.ssafy.deathnotelive.entity.User;
import com.ssafy.deathnotelive.repository.UserRepository;
import com.ssafy.deathnotelive.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Security 설정 Config
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    private final UserService userService;
    private final UserRepository userRepository;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // basic authentication
        http.httpBasic().disable() // basic authentication filter 비활성화
                .csrf().disable() //csrf 안씀
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS); //토큰 기반 인증(세션 X)
        // jwt filter
        http.addFilterBefore(
                new JwtAuthenticationFilter(authenticationManager()),
                UsernamePasswordAuthenticationFilter.class
        ).addFilterBefore(
                new JwtAuthorizationFilter(userRepository),
                BasicAuthenticationFilter.class
        );
        // authorization
        http.authorizeRequests()
                // /와 /home은 모두에게 허용
                .antMatchers("/", "/home", "/user/signup", "/user/login").permitAll()
                // hello 페이지는 USER 롤을 가진 유저에게만 허용
                .antMatchers("/note").hasRole("USER")
                .antMatchers("/admin").hasRole("ADMIN")
                .antMatchers(HttpMethod.POST, "/notice").hasRole("ADMIN")
                .antMatchers(HttpMethod.DELETE, "/notice").hasRole("ADMIN")
                .anyRequest().authenticated();

    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        // 정적 리소스 spring security 대상에서 제외
        web.ignoring()
                .antMatchers("/v2/api-docs",
                        "/configuration/ui",
                        "/swagger-resources/**",
                        "/configuration/security",
                        "/swagger-ui.html",
                        "/webjars/**");
    }

    /**
     * UserDetailsService 구현
     *
     * @return UserDetailsService
     */
    @Bean
    @Override
    public UserDetailsService userDetailsService() {
        return userId -> {
            User user = userService.findByUseId(userId);
            if (user == null) {
                throw new UsernameNotFoundException(userId);
            }
            return user;
        };
    }
}
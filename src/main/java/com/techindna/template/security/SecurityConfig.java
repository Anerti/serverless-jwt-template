package com.techindna.template.security;

import com.techindna.template.exception.ErrorBody;
import com.techindna.template.security.jwt.JwtAuthenticationFilter;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.servlet.DispatcherType;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    public ProxyManager<String> rateLimitBuckets(LettuceConnectionFactory connectionFactory) {
        RedisClient client = (RedisClient) connectionFactory.getRequiredNativeClient();
        return Bucket4jLettuce.casBasedBuilder(
                        client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)))
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                                Duration.ofSeconds(10)))
                .build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthFilter,
            RateLimitFilter rateLimitFilter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session ->
                                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(HttpMethod.GET, "/syn")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, "/auth/register")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, "/auth/login")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/auth/verify/*")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/auth/mfa/confirm/register/*",
                                                "/auth/mfa/confirm/login/*")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/users")
                                        .hasRole("ADMIN")
                                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class)
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(
                                                (request, response, authException) ->
                                                        ErrorBody.send(
                                                                response,
                                                                HttpStatus.UNAUTHORIZED,
                                                                "Authentication required."))
                                        .accessDeniedHandler(
                                                (request, response, accessDeniedException) ->
                                                        ErrorBody.send(
                                                                response,
                                                                HttpStatus.FORBIDDEN,
                                                                "Insufficient privileges.")));

        return http.build();
    }
}

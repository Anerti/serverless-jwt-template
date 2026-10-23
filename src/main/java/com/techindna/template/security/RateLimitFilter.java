package com.techindna.template.security;

import static com.techindna.template.config.RateLimitConfig.perMinute;
import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import com.techindna.template.exception.ErrorBody;
import com.techindna.template.service.redis.RateLimitService;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final RequestMatcher EXEMPT = pathPattern(HttpMethod.GET, "/syn");
    private static final RequestMatcher CRITICAL = pathPattern(HttpMethod.POST, "/auth/**");
    private static final RequestMatcher SENSITIVE =
            new OrRequestMatcher(
                    pathPattern(HttpMethod.GET, "/auth/**"),
                    pathPattern(HttpMethod.PATCH, "/users/**"),
                    pathPattern(HttpMethod.DELETE, "/users/**"));

    private final RateLimitService rateLimitService;
    private final ClientIpAddressResolver clientIpAddressResolver;

    @Value("${app.rate-limit.critical-per-minute}")
    private long criticalPerMinute;

    @Value("${app.rate-limit.sensitive-per-minute}")
    private long sensitivePerMinute;

    @Value("${app.rate-limit.standard-per-minute}")
    private long standardPerMinute;

    @Override
    protected boolean shouldNotFilter(@Nonnull HttpServletRequest request) {
        return EXEMPT.matches(request);
    }

    @Override
    protected void doFilterInternal(
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response,
            @Nonnull FilterChain filterChain)
            throws ServletException, IOException {
        Tier tier = tierOf(request);
        ConsumptionProbe probe;
        try {
            probe = rateLimitService.tryConsume(
                    tier.name() + ":ip:" + clientKey(clientIpAddressResolver.resolve(request)),
                    perMinute(tier.perMinute()));
        } catch (RuntimeException ex) {
            log.warn("Rate limit check failed, allowing request", ex);
            filterChain.doFilter(request, response);
            return;
        }

        if (!probe.isConsumed()) {
            long retryAfterSeconds =
                    Math.max(1, (long) Math.ceil(probe.getNanosToWaitForRefill() / 1e9));
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
            ErrorBody.send(
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests. Please try again later.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Tier tierOf(HttpServletRequest request) {
        return CRITICAL.matches(request) ? new Tier("critical", criticalPerMinute)
                : SENSITIVE.matches(request) ? new Tier("sensitive", sensitivePerMinute)
                : new Tier("standard", standardPerMinute);
    }

    static String clientKey(String ip) {
        byte[] address = InetAddress.ofLiteral(ip).getAddress();
        return address.length == 16 ? HexFormat.of().formatHex(address, 0, 8) + "/64" : ip;
    }

    private record Tier(String name, long perMinute) {}
}

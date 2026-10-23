package com.techindna.template.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.techindna.template.service.redis.RateLimitService;
import io.lettuce.core.RedisCommandTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class RateLimitFilterTest {

    @Test
    void redisFailureLetsRequestThrough() throws Exception {
        RateLimitService rateLimitService = mock(RateLimitService.class);
        when(rateLimitService.tryConsume(any(), any()))
                .thenThrow(new RedisCommandTimeoutException("Command timed out"));
        RateLimitFilter filter = new RateLimitFilter(rateLimitService, new ClientIpAddressResolver());
        ReflectionTestUtils.setField(filter, "criticalPerMinute", 10L);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("POST", "/auth/login"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void ipv6ClientsInSameSlash64ShareAKey() {
        assertThat(RateLimitFilter.clientKey("2001:db8:1:2:aaaa::1"))
                .isEqualTo(RateLimitFilter.clientKey("2001:db8:1:2:bbbb::2"))
                .isEqualTo("20010db800010002/64");
        assertThat(RateLimitFilter.clientKey("2001:db8:1:3::1")).isEqualTo("20010db800010003/64");
    }

    @Test
    void ipv4KeyIsUnchanged() {
        assertThat(RateLimitFilter.clientKey("203.0.113.9")).isEqualTo("203.0.113.9");
    }
}

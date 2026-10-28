package cn.communityelderly.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

class IdempotencyCaptureFilterTest {
    @Test
    void storesUtf8JsonWithoutUsingTheServletDefaultResponseCharset() throws Exception {
        IdempotencyService idempotencyService = mock(IdempotencyService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<IdempotencyService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(idempotencyService);
        IdempotencyCaptureFilter filter = new IdempotencyCaptureFilter(provider, new ObjectMapper());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/test/idempotency");
        request.addHeader(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, "utf8-response");
        request.setContentType("application/json");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        IdempotencyService.Reservation reservation = IdempotencyService.Reservation.owned(7L, "owner-token");
        request.setAttribute(IdempotencyInterceptor.RESERVATION_ATTRIBUTE, reservation);

        MockHttpServletResponse response = new IsoDefaultCharacterEncodingResponse();
        String firstResponse = "{\"message\":\"药盒打卡成功\"}";
        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("application/json");
            servletResponse.getOutputStream().write(firstResponse.getBytes(StandardCharsets.UTF_8));
        };

        filter.doFilter(request, response, chain);

        verify(idempotencyService).complete(eq(reservation), eq(200),
                argThat(firstResponse::equals), any());
        assertEquals(StandardCharsets.ISO_8859_1.name(), response.getCharacterEncoding());
        assertEquals(firstResponse, response.getContentAsString(StandardCharsets.UTF_8));
    }

    private static class IsoDefaultCharacterEncodingResponse extends MockHttpServletResponse {
        @Override
        public String getCharacterEncoding() {
            return StandardCharsets.ISO_8859_1.name();
        }
    }
}

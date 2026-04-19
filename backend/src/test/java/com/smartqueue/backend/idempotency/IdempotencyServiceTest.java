package com.smartqueue.backend.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link IdempotencyService}.
 *
 * Redis is mocked — no infrastructure needed.
 * Tests verify the serialisation/deserialisation contract and
 * the TTL used for cache entries.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyService")
class IdempotencyServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Simple test DTO
    record TestPayload(String token, int position) {}

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(redisTemplate, objectMapper);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── exists() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("exists()")
    class ExistsTests {

        @Test
        @DisplayName("returns true when Redis has the key")
        void returnsTrueWhenKeyExists() {
            when(redisTemplate.hasKey("idem:abc-123")).thenReturn(Boolean.TRUE);
            assertThat(idempotencyService.exists("abc-123")).isTrue();
        }

        @Test
        @DisplayName("returns false when Redis does not have the key")
        void returnsFalseWhenKeyAbsent() {
            when(redisTemplate.hasKey("idem:abc-123")).thenReturn(Boolean.FALSE);
            assertThat(idempotencyService.exists("abc-123")).isFalse();
        }

        @Test
        @DisplayName("returns false when Redis returns null (key expired between check and read)")
        void returnsFalseWhenRedisReturnsNull() {
            when(redisTemplate.hasKey("idem:xyz")).thenReturn(null);
            assertThat(idempotencyService.exists("xyz")).isFalse();
        }
    }

    // ── store() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("store()")
    class StoreTests {

        @Test
        @DisplayName("stores JSON under idem:{correlationId} with 10-min default TTL")
        void storesWithDefaultTtl() {
            TestPayload payload = new TestPayload("D1-T5", 3);
            idempotencyService.store("req-001", payload);

            ArgumentCaptor<String> keyCaptor   = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

            verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

            assertThat(keyCaptor.getValue()).isEqualTo("idem:req-001");
            assertThat(valueCaptor.getValue()).contains("D1-T5");
            assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofMinutes(10));
        }

        @Test
        @DisplayName("stores with custom TTL when explicitly provided")
        void storesWithCustomTtl() {
            idempotencyService.store("req-002", new TestPayload("D1-T9", 1), Duration.ofMinutes(5));

            ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
            verify(valueOps).set(anyString(), anyString(), ttlCaptor.capture());
            assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofMinutes(5));
        }

        @Test
        @DisplayName("store() does not throw when serialisation fails (non-fatal contract)")
        void doesNotThrowOnSerialisationFailure() {
            // ObjectMapper cannot serialise a raw Object with circular refs —
            // we simulate this by using a spy that throws
            ObjectMapper faultyMapper = spy(new ObjectMapper());
            IdempotencyService faultyService = new IdempotencyService(redisTemplate, faultyMapper);

            // Create an object that cannot be serialised
            Object unserializable = new Object() {
                public Object getSelf() { return this; }
            };

            // Should not throw — degrades gracefully
            assertThatNoException().isThrownBy(() -> faultyService.store("key", unserializable));

            // Redis was never called because serialisation failed
            verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
        }
    }

    // ── getResult() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getResult()")
    class GetResultTests {

        @Test
        @DisplayName("returns null when key does not exist in Redis")
        void returnsNullForMissingKey() {
            when(valueOps.get("idem:missing")).thenReturn(null);
            TestPayload result = idempotencyService.getResult("missing", TestPayload.class);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("deserialises and returns stored payload")
        void deserialisesPayloadCorrectly() throws Exception {
            String json = objectMapper.writeValueAsString(new TestPayload("D2-T3", 2));
            when(valueOps.get("idem:req-003")).thenReturn(json);

            TestPayload result = idempotencyService.getResult("req-003", TestPayload.class);

            assertThat(result).isNotNull();
            assertThat(result.token()).isEqualTo("D2-T3");
            assertThat(result.position()).isEqualTo(2);
        }

        @Test
        @DisplayName("returns null (not throws) when stored JSON is corrupted")
        void returnsNullForCorruptedJson() {
            when(valueOps.get("idem:bad")).thenReturn("NOT_VALID_JSON{{{");
            TestPayload result = idempotencyService.getResult("bad", TestPayload.class);
            assertThat(result).isNull();
        }
    }

    // ── Round-trip ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Round-trip: store then retrieve")
    class RoundTripTests {

        @Test
        @DisplayName("stored JSON is exactly what getResult would retrieve")
        void roundTrip() throws Exception {
            TestPayload original = new TestPayload("D3-T7", 5);
            String capturedJson = objectMapper.writeValueAsString(original);

            // Simulate store
            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
            idempotencyService.store("rt-001", original);
            verify(valueOps).set(eq("idem:rt-001"), valueCaptor.capture(), any(Duration.class));

            // Simulate retrieve with the same JSON
            when(valueOps.get("idem:rt-001")).thenReturn(valueCaptor.getValue());
            TestPayload retrieved = idempotencyService.getResult("rt-001", TestPayload.class);

            assertThat(retrieved).isNotNull();
            assertThat(retrieved.token()).isEqualTo(original.token());
            assertThat(retrieved.position()).isEqualTo(original.position());
        }
    }
}

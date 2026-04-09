package de.schneefisch.stepup.acr;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcrAspectTest {

    private AcrAspect aspect;
    private ProceedingJoinPoint joinPoint;

    @BeforeEach
    void setUp() {
        aspect = new AcrAspect();
        joinPoint = mock(ProceedingJoinPoint.class);
        SecurityContextHolder.clearContext();
    }

    private void authenticateWithAcr(String acrValue) {
        var claims = acrValue != null
                ? Map.<String, Object>of("acr", acrValue, "sub", "testuser")
                : Map.<String, Object>of("sub", "testuser");

        Jwt jwt = new Jwt(
                "dummy-token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"),
                claims
        );
        var auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private RequireAcr requireAcr(int level) {
        var annotation = mock(RequireAcr.class);
        when(annotation.value()).thenReturn(level);
        return annotation;
    }

    @Nested
    class WithNumericAcrValues {

        @Test
        void proceedsWhenExactLevelMatches() throws Throwable {
            authenticateWithAcr("1");
            when(joinPoint.proceed()).thenReturn("ok");

            assertDoesNotThrow(() -> aspect.checkAcr(joinPoint, requireAcr(1)));
            verify(joinPoint).proceed();
        }

        @Test
        void proceedsWhenHigherLevelThanRequired() throws Throwable {
            authenticateWithAcr("2");
            when(joinPoint.proceed()).thenReturn("ok");

            assertDoesNotThrow(() -> aspect.checkAcr(joinPoint, requireAcr(1)));
            verify(joinPoint).proceed();
        }

        @Test
        void throwsWhenLevelTooLow() {
            authenticateWithAcr("1");

            assertThrows(InsufficientAcrException.class,
                    () -> aspect.checkAcr(joinPoint, requireAcr(2)));
        }
    }

    @Nested
    class WithNamedAcrValues {

        @Test
        void proceedsWhenPasswordMeetsLevel1() throws Throwable {
            authenticateWithAcr("password");
            when(joinPoint.proceed()).thenReturn("ok");

            assertDoesNotThrow(() -> aspect.checkAcr(joinPoint, requireAcr(1)));
            verify(joinPoint).proceed();
        }

        @Test
        void throwsWhenPasswordDoesNotMeetLevel2() {
            authenticateWithAcr("password");

            assertThrows(InsufficientAcrException.class,
                    () -> aspect.checkAcr(joinPoint, requireAcr(2)));
        }

        @Test
        void proceedsWhenMfaMeetsLevel2() throws Throwable {
            authenticateWithAcr("mfa");
            when(joinPoint.proceed()).thenReturn("ok");

            assertDoesNotThrow(() -> aspect.checkAcr(joinPoint, requireAcr(2)));
            verify(joinPoint).proceed();
        }

        @Test
        void proceedsWhenMfaExceedsLevel1() throws Throwable {
            authenticateWithAcr("mfa");
            when(joinPoint.proceed()).thenReturn("ok");

            assertDoesNotThrow(() -> aspect.checkAcr(joinPoint, requireAcr(1)));
            verify(joinPoint).proceed();
        }
    }

    @Nested
    class WhenAcrIsMissing {

        @Test
        void throwsWhenAcrClaimIsNull() {
            authenticateWithAcr(null);

            assertThrows(InsufficientAcrException.class,
                    () -> aspect.checkAcr(joinPoint, requireAcr(1)));
        }

        @Test
        void throwsForUnknownAcrValue() {
            authenticateWithAcr("unknown");

            assertThrows(InsufficientAcrException.class,
                    () -> aspect.checkAcr(joinPoint, requireAcr(1)));
        }
    }
}

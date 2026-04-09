package de.schneefisch.stepup.acr;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Validates the ACR (Authentication Context Class Reference) claim in JWTs
 * against the level required by {@link RequireAcr}.
 *
 * <p>Keycloak sends ACR as a string that matches the client's {@code acr.loa.map}
 * configuration. This aspect maps those strings to numeric levels:
 * <ul>
 *   <li>{@code "password"} → level 1 (username + password)</li>
 *   <li>{@code "mfa"} → level 2 (password + OTP)</li>
 * </ul>
 * Numeric strings (e.g. {@code "1"}, {@code "2"}) are also accepted.
 */
@Aspect
@Component
public class AcrAspect {

    private static final Map<String, Integer> ACR_LEVEL_MAP = Map.of(
            "password", 1,
            "mfa", 2
    );

    @Around("@annotation(requireAcr)")
    public Object checkAcr(ProceedingJoinPoint joinPoint, RequireAcr requireAcr) throws Throwable {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            var jwt = jwtAuth.getToken();
            String acrClaim = jwt.getClaimAsString("acr");
            int currentLevel = parseAcrLevel(acrClaim);

            if (currentLevel < requireAcr.value()) {
                throw new InsufficientAcrException(requireAcr.value());
            }
        }

        return joinPoint.proceed();
    }

    private int parseAcrLevel(String acr) {
        if (acr == null || acr.isBlank()) {
            return 0;
        }
        // Try named ACR values first (from Keycloak acr.loa.map)
        Integer level = ACR_LEVEL_MAP.get(acr);
        if (level != null) {
            return level;
        }
        // Fall back to numeric parsing
        try {
            return Integer.parseInt(acr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

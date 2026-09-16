package cl.duoc.p360.bff;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;

import java.util.List;

/**
 * Azure AD emite dos formatos de token segun "accessTokenAcceptedVersion" del manifest:
 *
 *   v1.0 -> iss = https://sts.windows.net/<TENANT_ID>/
 *   v2.0 -> iss = https://login.microsoftonline.com/<TENANT_ID>/v2.0
 *
 * El caso pide v2.0. Este decoder acepta ambos para que la aplicacion siga
 * funcionando mientras el cambio de manifest se propaga, y en los dos casos
 * valida que la audiencia sea api://<API_CLIENT_ID>.
 *
 * Las claves publicas se descargan del mismo endpoint JWKS en ambos casos.
 */
@Configuration
@Profile("!local")
public class JwtDecoderConfig {

    @Value("${p360.azure.tenant-id}")
    private String tenantId;

    @Value("${p360.azure.api-client-id}")
    private String apiClientId;

    @Bean
    public JwtDecoder jwtDecoder() {
        String jwkSetUri = "https://login.microsoftonline.com/" + tenantId + "/discovery/v2.0/keys";

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        String issuerV1 = "https://sts.windows.net/" + tenantId + "/";
        String issuerV2 = "https://login.microsoftonline.com/" + tenantId + "/v2.0";
        String audience = "api://" + apiClientId;

        OAuth2TokenValidator<Jwt> issuerOk = jwt -> {
            String iss = jwt.getIssuer() == null ? "" : jwt.getIssuer().toString();
            if (issuerV1.equals(iss) || issuerV2.equals(iss)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_issuer",
                    "Issuer no reconocido: " + iss + ". Se esperaba " + issuerV1 + " o " + issuerV2,
                    null));
        };

        OAuth2TokenValidator<Jwt> audienceOk = jwt -> {
            List<String> aud = jwt.getAudience();
            if (aud != null && (aud.contains(audience) || aud.contains(apiClientId))) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_audience",
                    "Audiencia no valida: " + aud + ". Se esperaba " + audience,
                    null));
        };

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), issuerOk, audienceOk));

        return decoder;
    }
}

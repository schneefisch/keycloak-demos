package de.schneefisch.keycloak;

import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.IDToken;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A configurable OIDC protocol mapper that composes permission claims at token time
 * by resolving {@code ${placeholder}} variables in a template against group attributes.
 *
 * <p><b>Use case:</b> Instead of hard-coding permission strings on each client, store only a
 * {@code customer_id} (or similar identifier) as a group attribute. This mapper reads the
 * attribute value at token issuance and substitutes it into a permission template,
 * producing claims like {@code mqtt/customers/abc123/telemetry/#}.</p>
 *
 * <p><b>Configuration (Keycloak Admin UI):</b></p>
 * <ul>
 *   <li>{@code permission.template} — the permission pattern with {@code ${...}} placeholders</li>
 *   <li>{@code group.attribute.name} — the primary group attribute used to filter relevant groups</li>
 *   <li>{@code claim.name} — the token claim that will hold the resolved permission list</li>
 * </ul>
 */
public class TemplatePermissionMapper extends AbstractOIDCProtocolMapper
        implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

    public static final String PROVIDER_ID = "template-permission-mapper";

    /** Pattern matching {@code ${placeholder_name}} in the permission template. */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{(\\w+)}");

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES = List.of(
            buildProperty(
                    "permission.template",
                    "Permission Template",
                    "Template with ${...} placeholders resolved from group attributes. "
                            + "Example: mqtt/customers/${customer_id}/telemetry/#",
                    ProviderConfigProperty.STRING_TYPE,
                    null
            ),
            buildProperty(
                    "group.attribute.name",
                    "Group Attribute Name",
                    "The group attribute that identifies relevant groups. "
                            + "Groups without this attribute are skipped.",
                    ProviderConfigProperty.STRING_TYPE,
                    "customer_id"
            ),
            buildProperty(
                    "claim.name",
                    "Token Claim Name",
                    "Name of the claim added to the token containing the resolved permissions.",
                    ProviderConfigProperty.STRING_TYPE,
                    "mqtt_permissions"
            )
    );

    private static ProviderConfigProperty buildProperty(
            String name, String label, String helpText, String type, String defaultValue) {
        var property = new ProviderConfigProperty();
        property.setName(name);
        property.setLabel(label);
        property.setHelpText(helpText);
        property.setType(type);
        if (defaultValue != null) {
            property.setDefaultValue(defaultValue);
        }
        return property;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Template Permission Mapper";
    }

    @Override
    public String getDisplayCategory() {
        return TOKEN_MAPPER_CATEGORY;
    }

    @Override
    public String getHelpText() {
        return "Composes permission claims by resolving a template against group attributes.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    /**
     * Core mapping logic: reads the user's groups, resolves each {@code ${placeholder}} in the
     * template from group attributes, and sets the resulting list as a token claim.
     */
    @Override
    protected void setClaim(
            IDToken token,
            ProtocolMapperModel mappingModel,
            UserSessionModel userSession,
            KeycloakSession session,
            ClientSessionContext clientSessionCtx) {

        var config = mappingModel.getConfig();
        var template = config.get("permission.template");
        var attributeName = config.get("group.attribute.name");
        var claimName = config.get("claim.name");

        if (template == null || attributeName == null || claimName == null) {
            return;
        }

        var user = userSession.getUser();

        List<String> permissions = user.getGroupsStream()
                .filter(group -> group.getFirstAttribute(attributeName) != null)
                .map(group -> resolveTemplate(template, group))
                .toList();

        token.getOtherClaims().put(claimName, permissions);
    }

    /**
     * Replaces every {@code ${placeholder}} in the template with the corresponding attribute
     * value from the group. Unresolved placeholders are left as-is.
     */
    private String resolveTemplate(String template, GroupModel group) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        var result = new StringBuffer();
        while (matcher.find()) {
            String attrName = matcher.group(1);
            String replacement = group.getFirstAttribute(attrName);
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(replacement != null ? replacement : matcher.group(0)));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}

package de.schneefisch.keycloak;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.representations.AccessToken;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TemplatePermissionMapperTest {

    private final TemplatePermissionMapper mapper = new TemplatePermissionMapper();

    @Nested
    @DisplayName("Mapper metadata")
    class Metadata {

        @Test
        void providerIdIsTemplatePermissionMapper() {
            assertEquals("template-permission-mapper", mapper.getId());
        }

        @Test
        void displayTypeIsDescriptive() {
            assertTrue(mapper.getDisplayType().contains("Template"));
        }

        @Test
        void protocolIsOpenidConnect() {
            assertEquals("openid-connect", mapper.getProtocol());
        }

        @Test
        void configPropertiesIncludeTemplateAttributeNameAndClaimName() {
            var propertyNames = mapper.getConfigProperties().stream()
                    .map(p -> p.getName())
                    .toList();
            assertTrue(propertyNames.contains("permission.template"));
            assertTrue(propertyNames.contains("group.attribute.name"));
            assertTrue(propertyNames.contains("claim.name"));
        }
    }

    @Nested
    @DisplayName("Token transformation")
    class TokenTransformation {

        private KeycloakSession session;
        private UserSessionModel userSession;
        private ClientSessionContext clientSessionCtx;
        private UserModel user;
        private ProtocolMapperModel mapperModel;
        private AccessToken token;

        @BeforeEach
        void setUp() {
            var client = mock(ClientModel.class);
            var context = mock(KeycloakContext.class);
            session = mock(KeycloakSession.class);
            when(session.getContext()).thenReturn(context);
            when(context.getClient()).thenReturn(client);
            when(client.getAttribute("client.use.lightweight.access.token.enabled")).thenReturn("false");

            userSession = mock(UserSessionModel.class);
            clientSessionCtx = mock(ClientSessionContext.class);
            user = mock(UserModel.class);

            mapperModel = new ProtocolMapperModel();
            var config = new HashMap<String, String>();
            config.put("permission.template", "mqtt/customers/${customer_id}/telemetry/#");
            config.put("group.attribute.name", "customer_id");
            config.put("claim.name", "permissions");
            config.put("access.token.claim", "true");
            config.put("id.token.claim", "true");
            config.put("userinfo.token.claim", "true");
            mapperModel.setConfig(config);

            token = new AccessToken();

            when(userSession.getUser()).thenReturn(user);
        }

        @Test
        void resolvesTemplateFromSingleGroupAttribute() {
            var group = mockGroup("customer-a", "customer_id", "abc123");
            when(user.getGroupsStream()).thenReturn(Stream.of(group));

            mapper.transformAccessToken(token, mapperModel, session, userSession, clientSessionCtx);

            @SuppressWarnings("unchecked")
            var permissions = (List<String>) token.getOtherClaims().get("permissions");
            assertEquals(List.of("mqtt/customers/abc123/telemetry/#"), permissions);
        }

        @Test
        void resolvesTemplateFromMultipleGroups() {
            var groupA = mockGroup("customer-a", "customer_id", "abc123");
            var groupB = mockGroup("customer-b", "customer_id", "def456");
            when(user.getGroupsStream()).thenReturn(Stream.of(groupA, groupB));

            mapper.transformAccessToken(token, mapperModel, session, userSession, clientSessionCtx);

            @SuppressWarnings("unchecked")
            var permissions = (List<String>) token.getOtherClaims().get("permissions");
            assertEquals(
                    List.of("mqtt/customers/abc123/telemetry/#", "mqtt/customers/def456/telemetry/#"),
                    permissions
            );
        }

        @Test
        void skipsGroupsWithoutTheConfiguredAttribute() {
            var groupWithAttr = mockGroup("customer-a", "customer_id", "abc123");
            var groupWithout = mockGroup("other-group", "customer_id", null);
            when(user.getGroupsStream()).thenReturn(Stream.of(groupWithAttr, groupWithout));

            mapper.transformAccessToken(token, mapperModel, session, userSession, clientSessionCtx);

            @SuppressWarnings("unchecked")
            var permissions = (List<String>) token.getOtherClaims().get("permissions");
            assertEquals(List.of("mqtt/customers/abc123/telemetry/#"), permissions);
        }

        @Test
        void producesEmptyListWhenNoGroupsHaveTheAttribute() {
            var group = mockGroup("unrelated", "customer_id", null);
            when(user.getGroupsStream()).thenReturn(Stream.of(group));

            mapper.transformAccessToken(token, mapperModel, session, userSession, clientSessionCtx);

            @SuppressWarnings("unchecked")
            var permissions = (List<String>) token.getOtherClaims().get("permissions");
            assertTrue(permissions.isEmpty());
        }

        @Test
        void producesEmptyListWhenUserHasNoGroups() {
            when(user.getGroupsStream()).thenReturn(Stream.empty());

            mapper.transformAccessToken(token, mapperModel, session, userSession, clientSessionCtx);

            @SuppressWarnings("unchecked")
            var permissions = (List<String>) token.getOtherClaims().get("permissions");
            assertTrue(permissions.isEmpty());
        }

        @Test
        void supportsMultiplePlaceholdersInTemplate() {
            var config = new HashMap<String, String>();
            config.put("permission.template", "mqtt/${region}/customers/${customer_id}/data");
            config.put("group.attribute.name", "customer_id");
            config.put("claim.name", "permissions");
            config.put("access.token.claim", "true");
            config.put("id.token.claim", "true");
            config.put("userinfo.token.claim", "true");
            mapperModel.setConfig(config);

            var group = mock(GroupModel.class);
            when(group.getName()).thenReturn("customer-a");
            when(group.getFirstAttribute("customer_id")).thenReturn("abc123");
            when(group.getFirstAttribute("region")).thenReturn("eu-west");
            when(user.getGroupsStream()).thenReturn(Stream.of(group));

            mapper.transformAccessToken(token, mapperModel, session, userSession, clientSessionCtx);

            @SuppressWarnings("unchecked")
            var permissions = (List<String>) token.getOtherClaims().get("permissions");
            assertEquals(List.of("mqtt/eu-west/customers/abc123/data"), permissions);
        }

        private GroupModel mockGroup(String name, String attributeName, String attributeValue) {
            var group = mock(GroupModel.class);
            when(group.getName()).thenReturn(name);
            when(group.getFirstAttribute(attributeName)).thenReturn(attributeValue);
            return group;
        }
    }
}

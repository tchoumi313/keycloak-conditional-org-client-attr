package io.github.tchoumi313.keycloak.auth;

import io.phasetwo.service.model.OrganizationProvider;
import java.util.Map;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticator;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

public class ConditionalOrgAttributeMatchesClientAttribute implements ConditionalAuthenticator {

    private static final Logger LOG = Logger.getLogger(
            ConditionalOrgAttributeMatchesClientAttribute.class);

    static final ConditionalOrgAttributeMatchesClientAttribute SINGLETON =
            new ConditionalOrgAttributeMatchesClientAttribute();

    @Override
    public boolean matchCondition(AuthenticationFlowContext context) {
        Map<String, String> config = context.getAuthenticatorConfig() != null
                ? context.getAuthenticatorConfig().getConfig()
                : Map.of();

        String orgAttrName = config.get(
                ConditionalOrgAttributeMatchesClientAttributeFactory.CONF_ORG_ATTRIBUTE_NAME);
        String clientAttrName = config.get(
                ConditionalOrgAttributeMatchesClientAttributeFactory.CONF_CLIENT_ATTRIBUTE_NAME);
        boolean negateOutput = Boolean.parseBoolean(
                config.get(ConditionalOrgAttributeMatchesClientAttributeFactory.CONF_NEGATE));

        if (orgAttrName == null || orgAttrName.isBlank()
                || clientAttrName == null || clientAttrName.isBlank()) {
            LOG.warnf("Condition config incomplete: orgAttr=[%s], clientAttr=[%s]. Returning no-match.",
                    orgAttrName, clientAttrName);
            return negateOutput;
        }

        ClientModel client = context.getAuthenticationSession().getClient();
        String expectedValue = client.getAttribute(clientAttrName);

        if (expectedValue == null || expectedValue.isBlank()) {
            LOG.infof("Client [%s] has no attribute [%s]. Returning no-match.",
                    client.getClientId(), clientAttrName);
            return negateOutput;
        }

        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();
        UserModel user = context.getUser();

        OrganizationProvider orgProvider = session.getProvider(OrganizationProvider.class);
        if (orgProvider == null) {
            LOG.errorf("OrganizationProvider not available. Returning no-match.");
            return negateOutput;
        }

        boolean match = orgProvider
                .getUserOrganizationsStream(realm, user)
                .anyMatch(org -> {
                    boolean orgMatch;
                    try {
                        orgMatch = org.hasAttribute(orgAttrName, expectedValue);
                    } catch (NoSuchMethodError e) {
                        orgMatch = org.getAttributes()
                                .getOrDefault(orgAttrName, java.util.List.of())
                                .contains(expectedValue);
                    }
                    return orgMatch;
                });

        return negateOutput != match;
    }

    @Override
    public void action(AuthenticationFlowContext context) {
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public void close() {
    }
}

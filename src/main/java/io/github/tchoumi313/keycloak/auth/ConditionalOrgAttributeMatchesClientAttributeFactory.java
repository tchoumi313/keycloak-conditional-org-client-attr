package io.github.tchoumi313.keycloak.auth;

import java.util.List;
import org.keycloak.Config;
import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticator;
import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

public class ConditionalOrgAttributeMatchesClientAttributeFactory
        implements ConditionalAuthenticatorFactory {

    public static final String PROVIDER_ID = "conditional-org-client-attr";

    public static final String CONF_ORG_ATTRIBUTE_NAME = "org_attribute_name";
    public static final String CONF_CLIENT_ATTRIBUTE_NAME = "client_attribute_name";
    public static final String CONF_NEGATE = "negate_output";

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES = List.of(
            new ProviderConfigProperty(
                    CONF_ORG_ATTRIBUTE_NAME,
                    "Org attribute name",
                    "The organization attribute key to check (e.g. tenant_id).",
                    ProviderConfigProperty.STRING_TYPE,
                    ""
            ),
            new ProviderConfigProperty(
                    CONF_CLIENT_ATTRIBUTE_NAME,
                    "Client attribute name",
                    "The client attribute key that holds the expected value (e.g. tenant_id).",
                    ProviderConfigProperty.STRING_TYPE,
                    ""
            ),
            new ProviderConfigProperty(
                    CONF_NEGATE,
                    "Negate output",
                    "Invert the condition result.",
                    ProviderConfigProperty.BOOLEAN_TYPE,
                    "false"
            )
    );

    @Override
    public ConditionalAuthenticator getSingleton() {
        return ConditionalOrgAttributeMatchesClientAttribute.SINGLETON;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Condition - org attribute matches client attribute";
    }

    @Override
    public String getReferenceCategory() {
        return "condition";
    }

    @Override
    public String getHelpText() {
        return "Matches when the authenticating user belongs to an organization whose attribute equals the value of the specified attribute on the current OIDC client.";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[]{
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.DISABLED
        };
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    public void init(Config.Scope scope) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}

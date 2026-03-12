# PR Description: Conditional org attribute check with dynamic client attribute value

**Target repo:** `p2-inc/keycloak-orgs`
**Branch name suggestion:** `feat/conditional-org-attr-matches-client-attr`

---

## PR Title

feat: Add ConditionalAuthenticator that compares org attributes against client attributes at runtime

---

## PR Body

### Summary

Adds a new `ConditionalAuthenticator` (`conditional-org-attr-matches-client-attr`) that extends the existing org attribute condition pattern to support **dynamic expected values resolved from OIDC client attributes** at login time.

The existing `ConditionalOrgAttributeValue` (`ext-auth-conditional-org-attribute`) compares org attributes against a **static** value configured in the authenticator. This works for single-tenant flows but doesn't scale in multi-tenant setups where many OIDC clients share one realm and each client represents a different tenant.

### Problem

In a multi-tenant SaaS setup with:
- One realm, many OIDC clients (one per tenant)
- Phase Two organizations with a `tenant_id` attribute
- IdP-only authentication (no password login)

The current condition requires hardcoding the expected `tenant_id` in the authenticator config. This forces one of:
- A separate Post Broker Login flow per tenant (doesn't scale)
- A separate flow binding per IdP (ties tenants to IdPs, not clients)
- No org-attribute-based enforcement at all

### Solution

The new condition reads the expected value from the **current OIDC client's attributes** at runtime:

1. Admin sets `tenant_id = "acme-corp"` on the OIDC client entity
2. Admin sets `tenant_id = "acme-corp"` on the organization
3. At login, the condition reads `client.getAttribute("tenant_id")` → `"acme-corp"`
4. It checks: does the user belong to any org where `tenant_id` == `"acme-corp"`?
5. Returns match/no-match (with negate support), paired with Allow/Deny access

One realm-wide Post Broker Login flow handles all tenants dynamically.

### Config properties

| Property | Type | Description |
|----------|------|-------------|
| `org_attribute_name` | String | Org attribute key to check (e.g. `tenant_id`) |
| `client_attribute_name` | String | Client attribute key holding the expected value |
| `negate_output` | Boolean | Invert the match result |

### Design decisions

- **Fail closed**: Missing client attribute, missing config, or unavailable `OrganizationProvider` all return "no match". A misconfigured client cannot bypass tenant enforcement.
- **All orgs checked**: Always iterates all of the user's org memberships (equivalent to `allOrgs = true` in the existing condition). No dependency on `ACTIVE_ORGANIZATION` user attribute, since the client determines which org must match.
- **`requiresUser() = true`**: Must be placed in Post Broker Login or First Broker Login (not Browser flow). Documented in the help text.
- **Singleton pattern**: Matches the existing `ConditionalOrgAttributeValue` design — stateless, all context from `AuthenticationFlowContext`.

### Files added

```
src/main/java/io/phasetwo/service/auth/
├── ConditionalOrgAttributeMatchesClientAttribute.java       # Authenticator
└── ConditionalOrgAttributeMatchesClientAttributeFactory.java # Factory
```

Plus the `META-INF/services/org.keycloak.authentication.AuthenticatorFactory` entry.

### Relationship to existing code

This authenticator mirrors the structure of `ConditionalOrgAttributeValue` / `ConditionalOrgAttributeValueFactory` closely. Key difference is the source of the expected value:

| | Existing condition | This PR |
|-|-------------------|---------|
| Expected value source | Static config string | `client.getAttribute(key)` at runtime |
| Org scope | Active org or all orgs (toggle) | Always all orgs |
| Depends on ACTIVE_ORGANIZATION | Yes (when allOrgs=false) | No |
| Use case | Per-flow tenant enforcement | Per-client tenant enforcement |

### Testing

Tested against Phase Two Docker image with:
- Multiple OIDC clients with different `tenant_id` attributes
- Users belonging to multiple organizations
- IdP-only authentication (no password)
- Post Broker Login flow placement

| Scenario | Expected | Verified |
|----------|----------|----------|
| User in matching org, correct client | Login succeeds | ✅ |
| User in non-matching org | Access denied | ✅ |
| User in no org | Access denied | ✅ |
| Client missing `tenant_id` attribute | Access denied | ✅ |
| User in multiple orgs, one matches | Login succeeds | ✅ |
| Negate output ON | Behavior inverts | ✅ |

### Motivation / real-world context

I encountered this while building a multi-tenant platform where each OIDC client maps to a tenant, all sharing one realm and one set of IdPs. The existing `ConditionalOrgAttributeValue` got me 90% of the way — I just needed the expected value to come from the client instead of from static config. This is a natural extension of the existing pattern and I expect others in similar setups would benefit.

I also maintain a standalone version of this authenticator at [github.com/YOUR_USERNAME/keycloak-conditional-org-client-attr](https://github.com/YOUR_USERNAME/keycloak-conditional-org-client-attr) for users who prefer a separate JAR.

---

## Notes for Phase Two maintainers

- I'm happy to adjust the package structure, naming conventions, or config property keys to match your preferences.
- If you'd prefer this as an option within the existing `ConditionalOrgAttributeValue` (e.g. a new "Expected value source" toggle: static vs client attribute), I can refactor in that direction instead of a separate provider.
- The authenticator uses the same `OrganizationProvider` APIs as the existing condition. I adapted for method name differences I encountered in my version — let me know if anything needs adjustment for your current main branch.

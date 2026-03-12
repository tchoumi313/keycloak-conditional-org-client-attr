# keycloak-conditional-org-client-attr

A Keycloak ConditionalAuthenticator that enforces **per-client tenant access** by checking Phase Two organization attributes against OIDC client attributes at runtime.

## The Problem

In multi-tenant Keycloak setups using [Phase Two's keycloak-orgs](https://github.com/p2-inc/keycloak-orgs), you often need to restrict which users can log in to which client application based on organization membership. Phase Two ships a built-in condition (`Condition – organization attribute`) that checks org attributes, but it only supports **static** expected values configured directly in the authenticator. This means you need a separate auth flow (or a separate flow config) for each tenant — which doesn't scale.

## The Solution

This extension adds a new conditional authenticator: **"Condition - org attribute matches client attribute"**.

Instead of comparing an org attribute to a hardcoded string, it reads the expected value **from the OIDC client's attributes** at login time. This means:

- **One realm-wide Post Broker Login flow** handles all tenants
- Each OIDC client declares its own `tenant_id` (or any attribute name you choose)
- At login, the condition checks: *does this user belong to any organization where `tenant_id` matches this client's `tenant_id`?*
- If yes → login proceeds. If no → access denied.

## How It Works

```
User clicks login on Client X (tenant_id = "acme-corp")
    ↓
Browser flow redirects to IdP, user authenticates
    ↓
Post Broker Login fires (user is now identified)
    ↓
Condition reads Client X → getAttribute("tenant_id") → "acme-corp"
    ↓
Streams user's org memberships, checks:
  Does ANY org have tenant_id = "acme-corp"?
    ↓
  YES → Allow access          NO → Deny access
```

## Requirements

- **Keycloak 22+** (Quarkus distribution)
- **[Phase Two keycloak-orgs](https://github.com/p2-inc/keycloak-orgs)** deployed — this extension uses the `OrganizationProvider` SPI
- **Java 17+** and **Maven 3.8+** to build

## Installation

### Build

```bash
git clone https://github.com/YOUR_USERNAME/keycloak-conditional-org-client-attr.git
cd keycloak-conditional-org-client-attr
mvn clean package
```

### Deploy

Copy the JAR to your Keycloak providers directory:

```bash
# Docker / Kubernetes
cp target/keycloak-conditional-org-client-attr-1.0.0.jar /opt/keycloak/providers/

# Then rebuild (Quarkus)
/opt/keycloak/bin/kc.sh build
```

Or in a Dockerfile:

```dockerfile
COPY target/keycloak-conditional-org-client-attr-1.0.0.jar /opt/keycloak/providers/
RUN /opt/keycloak/bin/kc.sh build
```

Restart Keycloak. The provider registers automatically via `META-INF/services`.

## Configuration

### 1. Set client attributes

For each OIDC client representing a tenant:

**Admin Console → Clients → [your client] → Attributes**

| Key | Value |
|-----|-------|
| `tenant_id` | `acme-corp` |

### 2. Create the Post Broker Login flow

**Admin Console → Authentication → Flows → Create a copy of "Post Broker Login"**

Name it something like `Post Broker Login - Tenant Guard`, then add:

```
Post Broker Login - Tenant Guard
│
├── (CONDITIONAL) Subflow: Tenant Allowed
│   ├── (REQUIRED) Condition - org attribute matches client attribute
│   │     Org attribute name:       tenant_id
│   │     Client attribute name:    tenant_id
│   │     Negate output:            OFF
│   └── (REQUIRED) Allow access
│
├── (CONDITIONAL) Subflow: Tenant Denied
│   ├── (REQUIRED) Condition - org attribute matches client attribute
│   │     Org attribute name:       tenant_id
│   │     Client attribute name:    tenant_id
│   │     Negate output:            ON
│   └── (REQUIRED) Deny access
│         Error message:            "Access denied - not a member of this tenant"
```

### 3. Bind the flow

**Admin Console → Authentication → Bindings**

| Binding | Flow |
|---------|------|
| Post Broker Login Flow | `Post Broker Login - Tenant Guard` |

**Recommended**: Also create and bind a guarded First Broker Login flow to catch first-time users.

### 4. Set organization attributes

For each organization, set the matching attribute:

**Admin Console → Organizations → [org] → Attributes**

| Key | Value |
|-----|-------|
| `tenant_id` | `acme-corp` |

## Condition Config Reference

| Field | Description | Default |
|-------|-------------|---------|
| **Org attribute name** | The organization attribute key to check (e.g. `tenant_id`) | *(required)* |
| **Client attribute name** | The client attribute key holding the expected value | *(required)* |
| **Negate output** | Invert the result — use ON in the "denied" subflow | `false` |

## Security Model

This authenticator is **fail-closed** at every decision point:

| Scenario | Result |
|----------|--------|
| Client has no `tenant_id` attribute | **Denied** |
| Config fields are blank | **Denied** |
| OrganizationProvider not available | **Denied** |
| User belongs to no organizations | **Denied** |
| User belongs to orgs, none match | **Denied** |
| User belongs to org with matching attribute | **Allowed** |

## Important Notes

- **Placement matters**: This authenticator requires `context.getUser()` to be set. It **must** be placed in Post Broker Login or First Broker Login — not in the Browser flow before IdP redirect.
- **All orgs are checked**: The condition iterates all organizations the user is a member of. If *any* org matches, it returns true.
- **Client attributes, not protocol mappers**: The condition reads from `ClientModel.getAttribute()` — the key-value pairs on the client entity, not protocol mapper claims or client scopes.

## Debugging

The authenticator logs at INFO level with the format:

```
Condition result for user [username], client [client-id]: orgAttr [tenant_id] == clientAttr [tenant_id] (value=[acme-corp]) → match=true, negate=false, final=true
```

If you don't see this log line, the condition isn't executing — check your flow bindings.

## Compatibility

| Phase Two keycloak-orgs | Keycloak | Status |
|-------------------------|----------|--------|
| 0.130+ | 25.x - 26.x | Tested |
| 0.100 - 0.129 | 22.x - 24.x | Should work (may need method name adjustments) |

> **Note**: Phase Two's `OrganizationProvider` API evolves between versions. If the build fails, check method signatures in your deployed version (e.g. `getUserOrganizationsStream` vs `getByMember`, `hasAttribute` availability).

## License

Apache License 2.0

## Contributing

Issues and PRs welcome. If you find a Phase Two version where the API doesn't match, please open an issue with the version number and error.

## Acknowledgments

Built on top of [Phase Two's keycloak-orgs](https://github.com/p2-inc/keycloak-orgs) multi-tenancy extension. The authenticator design mirrors Phase Two's existing `ConditionalOrgAttributeValue` pattern.

package io.github.mortogo321.recon.api.security;

/**
 * Authorities used across the API. Deliberately three, mapping to the segregation of duties a
 * reconciliation function actually needs: whoever investigates a break cannot be the one who signs
 * it off, and neither of them can re-run the job.
 */
public final class ReconRoles {

    /** Works the exception queue: assign, comment, propose a resolution. */
    public static final String OPERATOR = "ROLE_OPERATOR";

    /** Signs off proposed resolutions. The checker in maker-checker. */
    public static final String APPROVER = "ROLE_APPROVER";

    /** Operates the job: launch, stop, restart, recover. */
    public static final String ADMIN = "ROLE_ADMIN";

    public static final String HAS_OPERATOR = "hasRole('OPERATOR')";
    public static final String HAS_APPROVER = "hasRole('APPROVER')";
    public static final String HAS_ADMIN = "hasRole('ADMIN')";
    public static final String HAS_ANY = "hasAnyRole('OPERATOR','APPROVER','ADMIN')";

    private ReconRoles() {}
}

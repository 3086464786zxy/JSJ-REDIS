package com.itmk.config.security.dto;

/** Always read from the primary database, outside cached permission snapshots. */
public record SecurityState(Long userId, String username, long globalVersion,
        long permissionVersion, long sessionVersion, boolean enabled, boolean revoked) {
    public String cacheVersion() { return globalVersion + ":" + permissionVersion; }
}

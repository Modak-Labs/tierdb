package io.modak.common;

/** Identity of a Modak-managed logical table (a Postgres relation OID). */
public record TableId(long oid) {}

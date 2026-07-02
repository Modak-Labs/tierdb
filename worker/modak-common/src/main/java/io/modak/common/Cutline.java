package io.modak.common;

/**
 * The cut-line: rows with {@code tier_key >= t} live in Postgres; {@code < t} live
 * in the cold base at version {@code snapshot}, overlaid by the delta. {@code t} and
 * {@code snapshot} are always advanced together as one atomic fact.
 */
public record Cutline(TierKey t, LakeSnapshotId snapshot) {}

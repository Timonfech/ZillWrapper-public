package com.zillya.timonfech.zillwrapper.core.interfaces;

/**
 * Marker interface for transient artifacts generated during an operation.
 * Artifacts are not persisted in the database.
 */
public interface IArtifact {
    String getFilename();
    byte[] getContent();
    String getMimeType();
}

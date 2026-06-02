package com.zillya.timonfech.zillwrapper.core.interfaces;

/**
 * Excel implementation of IArtifact.
 */
public record ExcelArtifact(String filename, byte[] content) implements IArtifact {
    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public byte[] getContent() {
        return content;
    }

    @Override
    public String getMimeType() {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

}

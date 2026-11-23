package org.mnu.domain;

import java.time.LocalDateTime;

public class FileAttachment {
    private long id;
    private long postId;
    private long uploadedBy;
    private String originalFilename;
    private String storedFilename;
    private long fileSize;
    private String mimeType;
    private LocalDateTime createdAt;

    public long getId() { return id; }
    public void setId(long v) { id = v; }

    public long getPostId() { return postId; }
    public void setPostId(long v) { postId = v; }

    public long getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(long v) { uploadedBy = v; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String v) { originalFilename = v == null ? "" : v; }

    public String getStoredFilename() { return storedFilename; }
    public void setStoredFilename(String v) { storedFilename = v == null ? "" : v; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long v) { fileSize = v; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String v) { mimeType = v == null ? "" : v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }
}

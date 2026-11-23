package org.mnu.service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.mnu.domain.*;
import org.mnu.mapper.FileMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {
    private final FileMapper mapper;
    private final Path uploadDir;

    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(
        Arrays.asList("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
                      "jpg", "jpeg", "png", "gif", "zip", "txt")
    );
    private static final Map<String, String> MIME_TYPES = new HashMap<>();
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_FILES_PER_POST = 5;

    static {
        MIME_TYPES.put("pdf", "application/pdf");
        MIME_TYPES.put("doc", "application/msword");
        MIME_TYPES.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        MIME_TYPES.put("xls", "application/vnd.ms-excel");
        MIME_TYPES.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        MIME_TYPES.put("ppt", "application/vnd.ms-powerpoint");
        MIME_TYPES.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("gif", "image/gif");
        MIME_TYPES.put("zip", "application/zip");
        MIME_TYPES.put("txt", "text/plain");
    }

    public FileService(FileMapper mapper, @Value("${file.upload.dir:}") String uploadDirPath) {
        this.mapper = mapper;
        if (uploadDirPath == null || uploadDirPath.isEmpty()) {
            this.uploadDir = Paths.get(System.getProperty("user.home"), "dev", "review-system", "uploads");
        } else {
            this.uploadDir = Paths.get(uploadDirPath);
        }
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new InputError("파일 저장소를 초기화할 수 없습니다.");
        }
    }

    @Transactional
    public FileAttachment upload(long postId, long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InputError("파일을 선택해주세요.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InputError("파일 크기는 10MB 이하여야 합니다.");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isEmpty()) {
            throw new InputError("파일명이 없습니다.");
        }

        int count = mapper.countByPost(postId);
        if (count >= MAX_FILES_PER_POST) {
            throw new InputError("게시글당 최대 " + MAX_FILES_PER_POST + "개까지 첨부할 수 있습니다.");
        }

        String ext = getExtension(originalName).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new InputError("허용되지 않는 파일 형식입니다. (" + String.join(", ", ALLOWED_EXTENSIONS) + ")");
        }

        String expectedMimeType = MIME_TYPES.get(ext);
        String actualMimeType = file.getContentType();
        if (actualMimeType == null) {
            actualMimeType = "application/octet-stream";
        }

        if (!mimeTypeMatches(actualMimeType, expectedMimeType)) {
            throw new InputError("파일 형식이 맞지 않습니다. 실제 파일을 업로드해주세요.");
        }

        String storedFilename = UUID.randomUUID().toString() + "." + ext;
        Path postDir = uploadDir.resolve(String.valueOf(postId));
        try {
            Files.createDirectories(postDir);
            Path filePath = postDir.resolve(storedFilename);
            Files.write(filePath, file.getBytes());
        } catch (IOException e) {
            throw new InputError("파일 저장 중 오류가 발생했습니다.");
        }

        FileAttachment fa = new FileAttachment();
        fa.setPostId(postId);
        fa.setUploadedBy(userId);
        fa.setOriginalFilename(originalName);
        fa.setStoredFilename(storedFilename);
        fa.setFileSize(file.getSize());
        fa.setMimeType(expectedMimeType);
        mapper.insert(fa);
        return fa;
    }

    public FileAttachment get(long id) {
        FileAttachment fa = mapper.selectById(id);
        if (fa == null) {
            throw new InputError("파일을 찾을 수 없습니다.", 404);
        }
        return fa;
    }

    public byte[] download(long id, long userId) {
        FileAttachment fa = get(id);
        if (fa.getUploadedBy() != userId) {
            throw new InputError("이 파일에 접근할 수 없습니다.", 403);
        }
        Path filePath = uploadDir.resolve(String.valueOf(fa.getPostId())).resolve(fa.getStoredFilename());
        try {
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new InputError("파일을 읽을 수 없습니다.");
        }
    }

    @Transactional
    public void delete(long id, long userId) {
        FileAttachment fa = get(id);
        if (fa.getUploadedBy() != userId) {
            throw new InputError("이 파일을 삭제할 수 없습니다.", 403);
        }
        int rows = mapper.delete(id, userId);
        if (rows != 1) {
            throw new InputError("파일을 삭제할 수 없습니다.");
        }
        Path filePath = uploadDir.resolve(String.valueOf(fa.getPostId())).resolve(fa.getStoredFilename());
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new InputError("파일 삭제 중 오류가 발생했습니다.");
        }
    }

    public List<Map<String, Object>> listByPost(long postId) {
        return mapper.selectByPost(postId);
    }

    public int countByPost(long postId) {
        return mapper.countByPost(postId);
    }

    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot <= 0) {
            throw new InputError("파일 확장자가 없습니다.");
        }
        return filename.substring(lastDot + 1);
    }

    private boolean mimeTypeMatches(String actualMimeType, String expectedMimeType) {
        if (expectedMimeType == null || actualMimeType.equals("application/octet-stream")) {
            return true;
        }
        return actualMimeType.equals(expectedMimeType) || actualMimeType.startsWith(expectedMimeType.split("/")[0]);
    }
}

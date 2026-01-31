package br.com.clube_quinze.api.service.media;

import br.com.clube_quinze.api.dto.media.MediaUploadResponse;
import br.com.clube_quinze.api.exception.BusinessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaStorageService {

    private static final List<String> ALLOWED_TYPES = List.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/webp"
    );

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024; // 10MB safeguard

    private final Path rootPath;
    private final String baseUrl;

    public MediaStorageService(
            @Value("${app.media.storage-path:/uploads}") String storagePath,
            @Value("${app.media.base-url:https://clubequinzeapp.cloud/uploads}") String baseUrl) {
        this.rootPath = Paths.get(storagePath).toAbsolutePath().normalize();
        this.baseUrl = trimTrailingSlash(baseUrl);
        try {
            Files.createDirectories(this.rootPath);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível criar diretório de uploads: " + this.rootPath, e);
        }
    }

    public MediaUploadResponse store(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Arquivo não enviado ou vazio");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException("Arquivo excede o limite de 10MB");
        }

        String contentType = safeContentType(file);
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException("Tipo de arquivo não suportado (apenas JPEG, PNG, WEBP)");
        }

        String sanitizedFolder = sanitizeFolder(folder);
        LocalDate now = LocalDate.now();
        String extension = resolveExtension(file.getOriginalFilename(), contentType);
        String filename = UUID.randomUUID() + extension;

        Path relativePath = Paths.get(sanitizedFolder, String.valueOf(now.getYear()), String.format("%02d", now.getMonthValue()), filename);
        Path target = rootPath.resolve(relativePath).normalize();
        if (!target.startsWith(rootPath)) {
            throw new BusinessException("Caminho de upload inválido");
        }

        try {
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("Falha ao salvar arquivo");
        }

        String url = baseUrl.isBlank()
                ? relativePath.toString().replace("\\", "/")
                : baseUrl + "/" + relativePath.toString().replace("\\", "/");

        return new MediaUploadResponse(url, relativePath.toString().replace("\\", "/"), file.getSize(), contentType);
    }

    private static String sanitizeFolder(String folder) {
        if (!StringUtils.hasText(folder)) {
            return "misc";
        }
        String cleaned = folder.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        return cleaned.isBlank() ? "misc" : cleaned;
    }

    private static String resolveExtension(String originalName, String contentType) {
        if (StringUtils.hasText(originalName) && originalName.contains(".")) {
            String ext = originalName.substring(originalName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
            if (ext.matches("\u002e(jpe?g|png|webp)")) {
                return ext;
            }
        }
        return switch (contentType) {
            case MediaType.IMAGE_PNG_VALUE -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg"; // jpeg fallback
        };
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private static String safeContentType(MultipartFile file) {
        String ct = file.getContentType();
        if (ct == null || ct.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return ct.toLowerCase(Locale.ROOT);
    }
}

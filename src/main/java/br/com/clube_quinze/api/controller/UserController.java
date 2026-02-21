package br.com.clube_quinze.api.controller;

import br.com.clube_quinze.api.dto.user.UpdateUserRequest;
import br.com.clube_quinze.api.dto.user.UserProfileResponse;
import br.com.clube_quinze.api.dto.user.UserSummary;
import br.com.clube_quinze.api.exception.UnauthorizedException;
import br.com.clube_quinze.api.security.ClubeQuinzeUserDetails;
import br.com.clube_quinze.api.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import br.com.clube_quinze.api.dto.user.UserGalleryPhotoRequest;
import br.com.clube_quinze.api.service.media.MediaStorageService;
import br.com.clube_quinze.api.exception.BusinessException;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Usuários")
public class UserController {

    private final UserService userService;
    private final MediaStorageService mediaStorageService;

    public UserController(UserService userService, MediaStorageService mediaStorageService) {
        this.userService = userService;
        this.mediaStorageService = mediaStorageService;
    }

    @GetMapping("/me")
    @Operation(summary = "Obter perfil do usuário autenticado")
    public ResponseEntity<UserProfileResponse> getCurrentUser(
            @AuthenticationPrincipal ClubeQuinzeUserDetails currentUser) {
        Long userId = extractUserId(currentUser);
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @GetMapping("/ping")
    @Operation(summary = "Verificar disponibilidade do endpoint de usuários")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("Isso tá funcionando");
    }

    @GetMapping
    @Operation(summary = "Listar membros cadastrados", description = "Retorna todos os usuários ativos e permite filtrar por membership tier (ex: 'QUINZE_STANDARD', 'QUINZE_PREMIUM', 'QUINZE_SELECT').")
    @PreAuthorize("hasAnyRole('CLUB_EMPLOYE','CLUB_ADMIN')")
    public ResponseEntity<List<UserSummary>> listUsers(
            @RequestParam(value = "membershipTier", required = false) String membershipTier) {
        return ResponseEntity.ok(userService.listMembers(membershipTier));
    }

    @PutMapping("/me")
    @Operation(summary = "Atualizar perfil (JSON)", description = "Use /me/upload para enviar arquivo")
    public ResponseEntity<UserProfileResponse> updateCurrentUser(
            @AuthenticationPrincipal ClubeQuinzeUserDetails currentUser,
            @Valid @RequestBody UpdateUserRequest request) {
        Long userId = extractUserId(currentUser);
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    @PutMapping(value = "/me/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Atualizar perfil com upload",
            description = "Multipart: part 'data' (JSON UpdateUserRequest) + 'profilePicture' opcional + 'gallery' opcional",
            responses = {
                @ApiResponse(responseCode = "200", description = "Perfil atualizado"),
                @ApiResponse(responseCode = "400", description = "Validação", content = @Content)
            })
    public ResponseEntity<UserProfileResponse> updateCurrentUserWithUpload(
            @AuthenticationPrincipal ClubeQuinzeUserDetails currentUser,
            @Valid @RequestPart("data") UpdateUserRequest request,
            @RequestPart(value = "profilePicture", required = false) MultipartFile profilePicture,
            @RequestPart(value = "gallery", required = false) List<MultipartFile> galleryFiles,
            @RequestPart(value = "galleryPosition", required = false) List<Integer> galleryPositions,
            @RequestPart(value = "folder", required = false) String folder) {
        Long userId = extractUserId(currentUser);
        UpdateUserRequest enriched = enrichWithUploads(request, profilePicture, galleryFiles, galleryPositions, folder);
        return ResponseEntity.ok(userService.updateProfile(userId, enriched));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Obter perfil por ID")
    public ResponseEntity<UserProfileResponse> getUserById(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Atualizar perfil por ID (JSON)", description = "Use /upload para enviar arquivo")
    @PreAuthorize("hasAnyRole('CLUB_EMPLOYE','CLUB_ADMIN')")
    public ResponseEntity<UserProfileResponse> updateUserById(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    @PutMapping(value = "/{userId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Atualizar perfil por ID com upload",
            description = "Multipart: part 'data' (JSON UpdateUserRequest) + 'profilePicture' opcional + 'gallery' opcional",
            responses = {
                @ApiResponse(responseCode = "200", description = "Perfil atualizado"),
                @ApiResponse(responseCode = "400", description = "Validação", content = @Content)
            })
    public ResponseEntity<UserProfileResponse> updateUserByIdWithUpload(
            @PathVariable Long userId,
            @Valid @RequestPart("data") UpdateUserRequest request,
            @RequestPart(value = "profilePicture", required = false) MultipartFile profilePicture,
            @RequestPart(value = "gallery", required = false) List<MultipartFile> galleryFiles,
            @RequestPart(value = "galleryPosition", required = false) List<Integer> galleryPositions,
            @RequestPart(value = "folder", required = false) String folder) {

        UpdateUserRequest enriched = enrichWithUploads(request, profilePicture, galleryFiles, galleryPositions, folder);
        return ResponseEntity.ok(userService.updateProfile(userId, enriched));
    }

    private UpdateUserRequest enrichWithUploads(
            UpdateUserRequest request,
            MultipartFile profilePicture,
            List<MultipartFile> galleryFiles,
            List<Integer> galleryPositions,
            String folder) {

        String cleanFolder = sanitizeFolder(folder);

        String profileUrl = request.profilePictureUrl();
        if (profilePicture != null && !profilePicture.isEmpty()) {
            var stored = mediaStorageService.store(profilePicture, cleanFolder);
            profileUrl = stored.url();
        }

        List<UserGalleryPhotoRequest> incoming = new ArrayList<>();
        if (request.gallery() != null) {
            incoming.addAll(request.gallery());
        }
        if (galleryFiles != null) {
            int fallback = 0;
            for (int i = 0; i < galleryFiles.size(); i++) {
                MultipartFile file = galleryFiles.get(i);
                if (file == null || file.isEmpty()) {
                    continue;
                }
                int position = (galleryPositions != null && galleryPositions.size() > i && galleryPositions.get(i) != null)
                        ? galleryPositions.get(i)
                        : fallback++;
                var stored = mediaStorageService.store(file, cleanFolder + "/gallery");
                incoming.add(new UserGalleryPhotoRequest(position, stored.url(), null));
            }
        }

        TreeMap<Integer, UserGalleryPhotoRequest> byPosition = new TreeMap<>();
        for (UserGalleryPhotoRequest photo : incoming) {
            if (photo == null) {
                continue;
            }
            Integer pos = photo.position();
            if (pos == null || pos < 0 || pos > 3) {
                throw new BusinessException("Posição da foto deve estar entre 0 e 3");
            }
            // Keep first occurrence per position
            byPosition.putIfAbsent(pos, new UserGalleryPhotoRequest(pos, photo.imageUrl(), null));
        }

        if (byPosition.size() > 4) {
            throw new BusinessException("Limite máximo de 4 fotos na galeria");
        }

        List<UserGalleryPhotoRequest> gallery = new ArrayList<>(byPosition.values());

        return new UpdateUserRequest(
                request.name(),
                request.email(),
                request.phone(),
                request.birthDate(),
                request.membershipTier(),
                request.planId(),
                profileUrl,
                null,
                gallery
        );
    }

    private String sanitizeFolder(String folder) {
        String base = (folder == null || folder.isBlank()) ? "users" : folder.trim().toLowerCase(Locale.ROOT);
        // allow only a-z, 0-9, /, _, -
        String cleaned = base.replaceAll("[^a-z0-9/_-]", "-");
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isBlank()) {
            cleaned = "users";
        }
        return cleaned;
    }

    private Long extractUserId(ClubeQuinzeUserDetails currentUser) {
        if (currentUser == null) {
            throw new UnauthorizedException("Usuário não autenticado");
        }
        return currentUser.getId();
    }
}

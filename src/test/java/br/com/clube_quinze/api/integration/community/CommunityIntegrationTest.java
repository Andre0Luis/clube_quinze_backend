package br.com.clube_quinze.api.integration.community;

import br.com.clube_quinze.api.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para CommunityController.
 *
 * Cenários cobertos:
 *  - Criar post (JSON)
 *  - Listar posts (feed)
 *  - Buscar post por ID
 *  - Comentar em post
 *  - Curtir post
 *  - Descurtir post
 *  - Deletar comentário
 *  - Deletar post
 *  - 401 operações sem autenticação
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Comunidade — Testes de Integração")
class CommunityIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/community/posts";
    private Long createdPostId;
    private Long createdCommentId;
    private String memberToken;

    @BeforeEach
    void setup() {
        String email = "comm_member_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        Map<String, Object> regReq = Map.of(
                "name", "Membro Comunidade",
                "email", email,
                "password", "Senha@1234",
                "membershipTier", "QUINZE_STANDARD"
        );
        ResponseEntity<Map> regRes = postPublic("/api/v1/auth/register", regReq, Map.class);
        assertThat(regRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        memberToken = (String) regRes.getBody().get("accessToken");
    }

    @Test
    @Order(1)
    @DisplayName("GET /community/posts → 200 lista feed de posts (autenticado)")
    void deveListarFeedDePosts() {
        ResponseEntity<Map> response = get(BASE, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("content");
    }

    @Test
    @Order(2)
    @DisplayName("POST /community/posts → 201 cria post com sucesso")
    void deveCriarPost() {
        Map<String, Object> request = Map.of(
                "title", "Post de Integração " + UUID.randomUUID().toString().substring(0, 6),
                "content", "Conteúdo do post de integração para validação do endpoint.",
                "media", List.of()
        );

        ResponseEntity<Map> response = post(BASE, request, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("id");
        assertThat(response.getBody()).containsKey("title");

        createdPostId = ((Number) response.getBody().get("id")).longValue();
    }

    @Test
    @Order(3)
    @DisplayName("GET /community/posts/{id} → 200 busca post por ID")
    void deveBuscarPostPorId() {
        if (createdPostId == null) deveCriarPost();

        ResponseEntity<Map> response = get(BASE + "/" + createdPostId, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("id")).isEqualTo(createdPostId.intValue());
    }

    @Test
    @Order(4)
    @DisplayName("POST /community/posts → 400 sem título ou conteúdo")
    void deveRetornar400SemCamposObrigatorios() {
        Map<String, Object> request = Map.of(
                "title", ""  // título vazio
        );

        ResponseEntity<Map> response = post(BASE, request, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(5)
    @DisplayName("POST /community/posts/{id}/comments → 201 adiciona comentário")
    void deveAdicionarComentario() {
        if (createdPostId == null) deveCriarPost();

        Map<String, String> request = Map.of("content", "Comentário de integração!");

        ResponseEntity<Map> response = post(BASE + "/" + createdPostId + "/comments", request, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("id");
        assertThat(response.getBody().get("content")).isEqualTo("Comentário de integração!");

        createdCommentId = ((Number) response.getBody().get("id")).longValue();
    }

    @Test
    @Order(6)
    @DisplayName("POST /community/posts/{id}/likes → 200 curtir post")
    void deveCurtirPost() {
        if (createdPostId == null) deveCriarPost();

        ResponseEntity<Map> response = post(BASE + "/" + createdPostId + "/likes", null, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("id");
    }

    @Test
    @Order(7)
    @DisplayName("DELETE /community/posts/{id}/likes → 204 descurtir post")
    void deveDescurtirPost() {
        if (createdPostId == null) deveCriarPost();
        // Garante que curtiu primeiro
        post(BASE + "/" + createdPostId + "/likes", null, memberToken, Map.class);

        ResponseEntity<Void> response = delete(BASE + "/" + createdPostId + "/likes", memberToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @Order(8)
    @DisplayName("DELETE /community/posts/{id}/comments/{commentId} → 204 deleta comentário")
    void deveDeletarComentario() {
        if (createdCommentId == null) deveAdicionarComentario();

        ResponseEntity<Void> response = delete(
                BASE + "/" + createdPostId + "/comments/" + createdCommentId, memberToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @Order(9)
    @DisplayName("DELETE /community/posts/{id} → 204 deleta post")
    void deveDeletarPost() {
        if (createdPostId == null) deveCriarPost();

        ResponseEntity<Void> response = delete(BASE + "/" + createdPostId, memberToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Valida remoção
        ResponseEntity<Map> getRes = get(BASE + "/" + createdPostId, memberToken, Map.class);
        assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(10)
    @DisplayName("POST /community/posts → 401 sem autenticação")
    void deveRetornar401SemAutenticacaoAoCriarPost() {
        Map<String, Object> request = Map.of(
                "title", "Post sem auth",
                "content", "Não deve ser criado"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(url(BASE), request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}

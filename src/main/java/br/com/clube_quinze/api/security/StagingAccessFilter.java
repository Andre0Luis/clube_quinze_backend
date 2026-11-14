package br.com.clube_quinze.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Porta de entrada do ambiente de homologação ("produção oculta").
 *
 * Só existe quando {@code app.staging.access-key} está definido (nunca em produção). Toda requisição precisa
 * trazer a chave no cabeçalho {@code X-Staging-Key}, no cookie {@code staging_key} ou no parâmetro
 * {@code ?staging_key=} (o parâmetro grava o cookie, para abrir o Swagger no navegador). Sem a chave a
 * resposta é 404, como se o endereço não existisse. Os caminhos de {@code app.staging.public-paths}
 * (links de e-mail e retorno do login do Google) passam sem chave, porque abrem direto no navegador.
 */
@Component
@Order(-200) // antes do Spring Security (-100): quem não tem a chave nem chega à autenticação
@ConditionalOnExpression("!'${app.staging.access-key:}'.isEmpty()")
public class StagingAccessFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Staging-Key";
    static final String COOKIE = "staging_key";
    static final String PARAM = "staging_key";

    private final byte[] expected;
    private final List<String> publicPaths;

    public StagingAccessFilter(@Value("${app.staging.access-key}") String accessKey,
                               @Value("${app.staging.public-paths:/appointment-action}") List<String> publicPaths) {
        this.expected = accessKey.getBytes(StandardCharsets.UTF_8);
        this.publicPaths = publicPaths.stream().map(String::trim).filter(p -> !p.isEmpty()).toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (publicPaths.stream().anyMatch(path::startsWith)) {
            chain.doFilter(request, response);
            return;
        }

        String fromQuery = request.getParameter(PARAM);
        if (matches(request.getHeader(HEADER)) || matches(cookieValue(request)) || matches(fromQuery)) {
            if (matches(fromQuery)) {
                Cookie cookie = new Cookie(COOKIE, fromQuery);
                cookie.setHttpOnly(true);
                cookie.setSecure(request.isSecure());
                cookie.setPath("/");
                cookie.setMaxAge(8 * 60 * 60);
                response.addCookie(cookie);
            }
            chain.doFilter(request, response);
            return;
        }
        // setStatus (e não sendError): sendError dispara o despacho de /error, que o Security responderia com 401.
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentLength(0);
    }

    private boolean matches(String candidate) {
        return candidate != null
                && MessageDigest.isEqual(expected, candidate.getBytes(StandardCharsets.UTF_8));
    }

    private String cookieValue(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (COOKIE.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}

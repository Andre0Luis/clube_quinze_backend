package br.com.clube_quinze.api.repository;

import br.com.clube_quinze.api.model.community.CommunityPost;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {

    @Override
    @EntityGraph(attributePaths = {"author", "media"})
    Page<CommunityPost> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"author", "media"})
    Page<CommunityPost> findByAuthorId(Long authorId, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "media"})
    Optional<CommunityPost> findDetailedById(Long postId);

}

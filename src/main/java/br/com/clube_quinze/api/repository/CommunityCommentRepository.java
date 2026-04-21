package br.com.clube_quinze.api.repository;

import br.com.clube_quinze.api.model.community.CommunityComment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {

    List<CommunityComment> findByPostId(Long postId);

    @Query("""
            select c
            from CommunityComment c
            where c.post.id in :postIds
            order by c.post.id asc, c.createdAt asc
            """)
    List<CommunityComment> findByPostIdInOrderByPostIdAndCreatedAt(
            @Param("postIds") Collection<Long> postIds);
}

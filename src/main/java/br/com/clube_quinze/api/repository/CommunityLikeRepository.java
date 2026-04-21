package br.com.clube_quinze.api.repository;

import br.com.clube_quinze.api.model.community.CommunityLike;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityLikeRepository extends JpaRepository<CommunityLike, Long> {

    interface PostLikeCount {

        Long getPostId();

        long getLikeCount();
    }

    Optional<CommunityLike> findByPostIdAndUserId(Long postId, Long userId);

    long countByPostId(Long postId);

    @Query("""
            select l.post.id as postId, count(l.id) as likeCount
            from CommunityLike l
            where l.post.id in :postIds
            group by l.post.id
            """)
    List<PostLikeCount> countGroupedByPostIds(@Param("postIds") Collection<Long> postIds);
}

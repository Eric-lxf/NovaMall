package com.ruoyi.blog.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.blog.domain.BlogArticle;

@Mapper
public interface BlogArticleMapper extends BaseMapper<BlogArticle>
{

    @Update("UPDATE blog_article SET view_count = IFNULL(view_count, 0) + 1 WHERE id = #{id} AND is_deleted = 0")
    int incrementViewCount(@Param("id") Long id);

    @Select("""
            <script>
            SELECT COUNT(*) FROM blog_article WHERE is_deleted = 1
            <if test='keyword != null and keyword != ""'>
              AND (title LIKE CONCAT('%', #{keyword}, '%') OR summary LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            </script>
            """)
    long countRecycle(@Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT id, title, summary, content, html_content, cover_image, category_id,
                   source_type, source_client_id, external_id, status,
                   is_ai_generated, view_count, create_time, update_time, is_deleted
            FROM blog_article WHERE is_deleted = 1
            <if test='keyword != null and keyword != ""'>
              AND (title LIKE CONCAT('%', #{keyword}, '%') OR summary LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY update_time DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<BlogArticle> selectRecycleList(@Param("keyword") String keyword, @Param("offset") long offset,
            @Param("limit") long limit);

    @Select("""
            SELECT id, title, summary, content, html_content, cover_image, category_id,
                   source_type, source_client_id, external_id, status,
                   is_ai_generated, view_count, create_time, update_time, is_deleted
            FROM blog_article WHERE id = #{id} AND is_deleted = 1
            """)
    BlogArticle selectDeletedById(@Param("id") Long id);

    @Update("UPDATE blog_article SET is_deleted = 0 WHERE id = #{id} AND is_deleted = 1")
    int restoreById(@Param("id") Long id);

    @Delete("DELETE FROM blog_article WHERE id = #{id} AND is_deleted = 1")
    int purgeById(@Param("id") Long id);
}

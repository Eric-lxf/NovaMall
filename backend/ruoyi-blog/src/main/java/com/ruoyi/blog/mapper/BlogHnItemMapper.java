package com.ruoyi.blog.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.blog.domain.BlogHnItem;
import com.ruoyi.blog.vo.BlogHnListItemVO;

@Mapper
public interface BlogHnItemMapper extends BaseMapper<BlogHnItem>
{

    @Select("""
            <script>
            SELECT i.id, i.hn_id, i.title_en, i.title_zh, i.summary_zh, i.score, i.author,
                   i.comment_count, i.hn_time, i.translate_status, i.status, r.`rank` AS rank
            FROM blog_hn_rank r
            INNER JOIN blog_hn_item i ON i.hn_id = r.hn_id
            WHERE r.board = #{board} AND r.snapshot_at = #{snapshotAt}
            <if test='keyword != null and keyword != ""'>
              AND (i.title_en LIKE CONCAT('%', #{keyword}, '%') OR i.title_zh LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test='translateStatus != null and translateStatus != ""'>
              AND i.translate_status = #{translateStatus}
            </if>
            ORDER BY r.`rank` ASC
            </script>
            """)
    Page<BlogHnListItemVO> selectAdminBoardPage(Page<BlogHnListItemVO> page, @Param("board") String board,
            @Param("snapshotAt") LocalDateTime snapshotAt, @Param("keyword") String keyword,
            @Param("translateStatus") String translateStatus);

    @Select("""
            SELECT i.hn_id, i.title_en, i.title_zh, i.summary_zh, i.url, i.hn_url, i.score, i.author,
                   i.comment_count, i.hn_time, r.`rank` AS rank
            FROM blog_hn_rank r
            INNER JOIN blog_hn_item i ON i.hn_id = r.hn_id
            WHERE r.board = #{board} AND r.snapshot_at = #{snapshotAt}
              AND i.status = 1 AND i.title_zh IS NOT NULL AND i.title_zh &lt;&gt; ''
            ORDER BY r.`rank` ASC
            """)
    Page<BlogHnListItemVO> selectPublicBoardPage(Page<BlogHnListItemVO> page, @Param("board") String board,
            @Param("snapshotAt") LocalDateTime snapshotAt);
}

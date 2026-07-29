package com.ruoyi.blog.service.hn;

import java.util.Map;

public interface HnSyncService
{

    /**
     * @return false if board already syncing
     */
    boolean syncBoardAsync(String board);

    void syncBoard(String board);

    void syncAll();

    Map<String, Object> syncStatus();
}

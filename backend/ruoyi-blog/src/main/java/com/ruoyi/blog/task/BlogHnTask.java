package com.ruoyi.blog.task;

import org.springframework.stereotype.Component;

import com.ruoyi.blog.service.hn.HnSyncService;

import lombok.RequiredArgsConstructor;

@Component("blogHnTask")
@RequiredArgsConstructor
public class BlogHnTask
{
    private final HnSyncService hnSyncService;

    public void syncAll()
    {
        hnSyncService.syncAll();
    }
}

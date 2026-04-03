package com.IntelliRecipe.Kilsme.tools;

import com.IntelliRecipe.Kilsme.service.ListService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SoftDeleteCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(SoftDeleteCleanupTask.class);

    @Autowired
    private ListService listService;

    // 每天凌晨 2 点执行：清理 shopping_list_items 中已逻辑删除的数据
    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Shanghai")
    public void cleanupLogicalDeletedItems() {
        int deleted = listService.cleanLogicalDeletedItems();
        log.info("[SoftDeleteCleanupTask] finished, physically deleted rows={}", deleted);
    }
}


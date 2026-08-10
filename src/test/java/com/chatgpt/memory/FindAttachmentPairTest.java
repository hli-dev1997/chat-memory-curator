package com.chatgpt.memory;

import com.chatgpt.memory.mapper.SessionBoundaryPairMapper;
import com.chatgpt.memory.model.entity.SessionBoundaryPairDO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@Slf4j
@SpringBootTest
public class FindAttachmentPairTest {

    @Autowired
    private SessionBoundaryPairMapper sessionBoundaryPairMapper;

    @Test
    public void findPairsWithAttachments() {
        List<SessionBoundaryPairDO> list = sessionBoundaryPairMapper.selectUncheckedStream(null, 1, 50);
        log.info("Found {} unchecked pairs with hasAttachment = 1", list.size());
        for (SessionBoundaryPairDO pair : list) {
            log.info("Attachment Pair ID: {}, convId: {}, index: {}, textA_has_img: {}, textB_has_img: {}",
                    pair.getId(), pair.getParentConversationId(), pair.getPairIndex(),
                    pair.getMessageAText() != null && pair.getMessageAText().contains("![图片]"),
                    pair.getMessageBText() != null && pair.getMessageBText().contains("![图片]"));
        }
    }
}

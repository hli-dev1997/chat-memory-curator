package com.chatgpt.memory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ChatAssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testServeSedimentAssetWithProtocol() throws Exception {
        // 测试包含 sediment:// 协议头以及缺失 -sanitized.jpg 扩展名时的模糊兼容定位
        mockMvc.perform(get("/chat-assets/sediment://file_00000000654872099dafc576c2d84731"))
                .andExpect(status().isOk());
    }

    @Test
    public void testServeAssetWithCleanName() throws Exception {
        // 测试只使用基础 ID file_00000000654872099dafc576c2d84731 时的定位
        mockMvc.perform(get("/chat-assets/file_00000000654872099dafc576c2d84731"))
                .andExpect(status().isOk());
    }
}

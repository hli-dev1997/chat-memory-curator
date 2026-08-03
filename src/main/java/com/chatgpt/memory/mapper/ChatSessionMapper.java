package com.chatgpt.memory.mapper;

import com.chatgpt.memory.model.entity.ChatSessionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 类说明 / Class Description:
 * 中文：chat_session 数据库表 MyBatis 映射 Mapper 接口。
 * English: MyBatis Mapper interface for chat_session database table.
 * <p>
 * 设计目的 / Design Purpose:
 * 中文：提供表初始化 DDL、动态补全字段、修补字段类型、批量插入/更新 (ON DUPLICATE KEY UPDATE) 与按 Session ID 查询功能。
 * English: Provide table DDL init, column migration, batch insert/upsert, and query operations.
 * </p>

 * @author Antigravity
 */
@Mapper
public interface ChatSessionMapper {

    /**
     * 初始化创建 chat_session 表（若不存在）
     */
    void createTableIfNotExists();

    /**
     * 动态补全 user_id 字段（兼容已存在旧表）
     */
    void addColumnUserIdIfNotExists();

    /**
     * 动态补全 source 字段（兼容已存在旧表）
     */
    void addColumnSourceIfNotExists();

    /**
     * 动态补全 platform 字段（兼容已存在旧表）
     */
    void addColumnPlatformIfNotExists();

    /**
     * 动态修补 session_json 字段类型为 JSON 类型
     */
    void modifyColumnSessionJsonToJson();

    /**
     * 插入单条 Session 记录（冲突则更新）
     *
     * @param record ChatSessionDO 实体
     * @return 影响行数
     */
    int upsert(ChatSessionDO record);

    /**
     * 批量插入或更新 Session 记录
     *
     * @param list 实体列表
     * @return 影响行数
     */
    int batchUpsert(@Param("list") List<ChatSessionDO> list);

    /**
     * 根据 sessionId 查询单个 Session 实体
     *
     * @param sessionId 片段 ID
     * @return ChatSessionDO
     */
    ChatSessionDO selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 查询记录总条数
     *
     * @return 记录数
     */
    long count();
}

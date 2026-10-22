-- Clean schema only; no historical users or conversations.
SET NAMES utf8mb4;
CREATE TABLE `app` (
  `app_id` bigint NOT NULL AUTO_INCREMENT COMMENT '应用ID',
  `app_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '应用名',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '应用介绍',
  `init_prompt` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '初始化提示',
  `cover` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '封面图片URL',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `prologue` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '开场白',
  `priority` int DEFAULT NULL COMMENT '优先级',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `edit_time` datetime DEFAULT NULL COMMENT '修改时间',
  `is_delete` tinyint DEFAULT '0' COMMENT '是否删除（0=未删，1=已删）',
  PRIMARY KEY (`app_id`) USING BTREE,
  KEY `idx_userId` (`user_id`) USING BTREE,
  KEY `idx_create_time` (`create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='应用表';

CREATE TABLE `chat_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息',
  `message_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'user/ai',
  `conversation_id` varchar(64) NOT NULL DEFAULT 'legacy',
  `turn_id` varchar(64) DEFAULT NULL,
  `app_id` bigint NOT NULL COMMENT '应用id',
  `user_id` bigint NOT NULL COMMENT '创建用户id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_appId` (`app_id`) USING BTREE,
  KEY `idx_createTime` (`create_time`) USING BTREE,
  KEY `idx_appId_createTime` (`app_id`,`create_time`) USING BTREE,
  KEY `idx_user_app_conversation` (`user_id`,`app_id`,`conversation_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='对话历史';

CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `user_account` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '账号',
  `user_password` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码',
  `user_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户昵称',
  `user_avatar` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户头像',
  `user_profile` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户简介',
  `user_role` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'user' COMMENT '用户角色：user/admin',
  `edit_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '编辑时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_userAccount` (`user_account`) USING BTREE,
  KEY `idx_userName` (`user_name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='用户';
CREATE TABLE voice_feedback (
  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id bigint NOT NULL,
  app_id bigint NOT NULL,
  conversation_id varchar(64) NOT NULL,
  turn_id varchar(64) NOT NULL,
  category varchar(32) NOT NULL,
  elapsed_ms bigint DEFAULT NULL,
  created_at timestamp DEFAULT CURRENT_TIMESTAMP,
  KEY idx_feedback_user_conversation (user_id, conversation_id, turn_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO app (app_id, app_name, description, init_prompt, cover, user_id, prologue, priority)
VALUES (1, '苏格拉底', '用温和的提问一起理清思路', '你是苏格拉底。通过简短、温和的追问帮助对方思考，每次最多提出一个问题。不要声称自己是真实历史人物。', '/portraits/socrates.svg', 0, '你好，我的朋友。今天你想一起探讨什么？', 99),
(2, '福尔摩斯', '观察细节，练习推理与表达', '你扮演文学角色福尔摩斯，以观察和推理陪伴用户交流。区分事实与假设，回答简短自然，不虚构用户提供以外的证据。', '/portraits/holmes.svg', 0, '你好。有什么值得我们一起观察的线索吗？', 99);

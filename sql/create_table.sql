-- 创建库
create database if not exists yu_picture;

-- 切换库
use yu_picture;
-- 追加到 sql/create_table.sql

-- 聊天记忆表
create table if not exists chat_memory
(
    id              bigint auto_increment comment 'id' primary key,
    conversation_id varchar(128) not null comment '会话ID',
    message_type    varchar(32)  not null comment '消息类型: USER/ASSISTANT/SYSTEM',
    content         text         not null comment '消息内容',
    create_time     datetime default CURRENT_TIMESTAMP comment '创建时间',
    INDEX idx_conversation_id (conversation_id)
) comment '聊天记忆' collate = utf8mb4_unicode_ci;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '用户' collate = utf8mb4_unicode_ci;

-- 图片表
create table if not exists picture
(
    id           bigint auto_increment comment 'id' primary key,
    url          varchar(512)                       not null comment '图片 url',
    name         varchar(128)                       not null comment '图片名称',
    introduction varchar(512)                       null comment '简介',
    category     varchar(64)                        null comment '分类',
    tags         varchar(512)                       null comment '标签（JSON 数组）',
    picSize      bigint                             null comment '图片体积',
    picWidth     int                                null comment '图片宽度',
    picHeight    int                                null comment '图片高度',
    picScale     double                             null comment '图片宽高比例',
    picFormat    varchar(32)                        null comment '图片格式',
    userId       bigint                             not null comment '创建用户 id',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    editTime     datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    INDEX idx_name (name),                 -- 提升基于图片名称的查询性能
    INDEX idx_introduction (introduction), -- 用于模糊搜索图片简介
    INDEX idx_category (category),         -- 提升基于分类的查询性能
    INDEX idx_tags (tags),                 -- 提升基于标签的查询性能
    INDEX idx_userId (userId)              -- 提升基于用户 ID 的查询性能
) comment '图片' collate = utf8mb4_unicode_ci;


ALTER TABLE picture
    -- 添加新列
    ADD COLUMN reviewStatus INT DEFAULT 0 NOT NULL COMMENT '审核状态：0-待审核; 1-通过; 2-拒绝',
    ADD COLUMN reviewMessage VARCHAR(512) NULL COMMENT '审核信息',
    ADD COLUMN reviewerId BIGINT NULL COMMENT '审核人 ID',
    ADD COLUMN reviewTime DATETIME NULL COMMENT '审核时间';

-- 创建基于 reviewStatus 列的索引
CREATE INDEX idx_reviewStatus ON picture (reviewStatus);

ALTER TABLE picture
    -- 添加新列
    ADD COLUMN thumbnailUrl varchar(512) NULL COMMENT '缩略图 url';


-- 空间表
create table if not exists space
(
    id         bigint auto_increment comment 'id' primary key,
    spaceName  varchar(128)                       null comment '空间名称',
    spaceLevel int      default 0                 null comment '空间级别：0-普通版 1-专业版 2-旗舰版',
    maxSize    bigint   default 0                 null comment '空间图片的最大总大小',
    maxCount   bigint   default 0                 null comment '空间图片的最大数量',
    totalSize  bigint   default 0                 null comment '当前空间下图片的总大小',
    totalCount bigint   default 0                 null comment '当前空间下的图片数量',
    userId     bigint                             not null comment '创建用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    editTime   datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    -- 索引设计
    index idx_userId (userId),        -- 提升基于用户的查询效率
    index idx_spaceName (spaceName),  -- 提升基于空间名称的查询效率
    index idx_spaceLevel (spaceLevel) -- 提升按空间级别查询的效率
) comment '空间' collate = utf8mb4_unicode_ci;

-- 添加新列
ALTER TABLE picture
    ADD COLUMN spaceId bigint  null comment '空间 id（为空表示公共空间）';

-- 创建索引
CREATE INDEX idx_spaceId ON picture (spaceId);

-- 添加新列
ALTER TABLE picture
    ADD COLUMN picColor varchar(16) null comment '图片主色调';

-- 支持空间类型，添加新列
ALTER TABLE space
    ADD COLUMN spaceType int default 0 not null comment '空间类型：0-私有 1-团队';

CREATE INDEX idx_spaceType ON space (spaceType);

-- 空间成员表
create table if not exists space_user
(
    id         bigint auto_increment comment 'id' primary key,
    spaceId    bigint                                 not null comment '空间 id',
    userId     bigint                                 not null comment '用户 id',
    spaceRole  varchar(128) default 'viewer'          null comment '空间角色：viewer/editor/admin',
    createTime datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    -- 索引设计
    UNIQUE KEY uk_spaceId_userId (spaceId, userId), -- 唯一索引，用户在一个空间中只能有一个角色
    INDEX idx_spaceId (spaceId),                    -- 提升按空间查询的性能
    INDEX idx_userId (userId)                       -- 提升按用户查询的性能
) comment '空间用户关联' collate = utf8mb4_unicode_ci;

-- 扩展用户表：新增会员功能
ALTER TABLE user
    ADD COLUMN vipExpireTime datetime NULL COMMENT '会员过期时间',
    ADD COLUMN vipCode varchar(128) NULL COMMENT '会员兑换码',
    ADD COLUMN vipNumber bigint NULL COMMENT '会员编号';


use yu_picture;
-- 切换库
-- 恋爱知识库表（RAG降级查询兜底）
create table if not exists love_knowledge
(
    id         bigint auto_increment comment 'id' primary key,
    content    text         not null comment '知识内容',
    status     varchar(32)  not null comment '状态标签：单身/恋爱/已婚',
    tags       varchar(512) null comment '关键词标签，用于降级检索匹配',
    createTime datetime default CURRENT_TIMESTAMP comment '创建时间'
) comment '恋爱知识库' collate = utf8mb4_unicode_ci;


-- 恋爱知识库示例数据
INSERT INTO love_knowledge (content, status, tags) VALUES
-- 单身状态
('拓展社交圈是脱单的第一步。建议参加兴趣社团、行业交流会、朋友聚会等活动，增加认识新朋友的机会。不要局限于固定的社交圈，多尝试新事物，提升自己吸引力的同时也能遇到志同道合的人。', '单身', '社交圈,拓展,脱单,认识新朋友'),
('追求心仪对象时，首先要了解对方的兴趣和喜好，找到共同话题。不要急于表白，先建立友谊和信任。展示真实的自己，不要刻意伪装。适当的关心和帮助能拉近距离，但要注意把握分寸，不要给对方造成压力。', '单身', '追求,表白,共同话题,心仪对象'),
('单身期间要注重自我提升，包括外在形象和内在修养。保持健康的生活习惯，培养自己的兴趣爱好，提升职业能力。一个自信、独立、有趣的人更容易吸引到合适的伴侣。', '单身', '自我提升,自信,独立,个人成长'),
('网恋需要注意安全，不要轻易透露个人隐私信息。建议先通过视频通话确认对方身份，再考虑线下见面。见面时选择公共场所，告知朋友或家人。网络交友要保持理性，不要被甜言蜜语冲昏头脑。', '单身', '网恋,安全,交友,线上认识'),
('30岁左右的单身人群，要明确自己的择偶标准，不要过于理想化。重点关注对方的三观、性格和人品，而不是单纯看外表和物质条件。可以通过靠谱的婚恋平台或者亲友介绍来扩大选择范围。', '单身', '30岁,择偶标准,三观,婚恋平台'),

-- 恋爱状态
('恋爱中沟通是关键。遇到问题要及时沟通，不要冷战或者积压情绪。表达感受时用"我觉得"而不是"你总是"，避免指责对方。倾听对方的想法，理解彼此的立场，共同寻找解决方案。', '恋爱', '沟通,冷战,情绪,表达感受'),
('恋爱中的矛盾和争吵是正常的，关键是如何处理。吵架时不要说伤人的话，不要翻旧账。学会换位思考，适当妥协。如果情绪激动，可以先冷静一下再沟通，避免在冲动时做决定。', '恋爱', '矛盾,争吵,处理冲突,换位思考'),
('恋爱中要保持适当的个人空间。不要过度依赖对方，也不要限制对方的自由。每个人都需要独处的时间和自己的朋友圈。健康的恋爱关系是两个独立个体的相互吸引，而不是相互束缚。', '恋爱', '个人空间,依赖,自由,独立'),
('异地恋需要更多的信任和沟通。建议每天保持联系，分享日常生活。定期见面很重要，可以轮流去对方的城市。共同规划未来，给彼此信心和安全感。不要过度猜疑，信任是异地恋的基石。', '恋爱', '异地恋,信任,沟通,定期见面'),
('恋爱中要学会制造浪漫和惊喜。不需要昂贵的礼物，一个用心的小举动就能让对方感动。记住重要的纪念日，偶尔准备小惊喜。保持约会习惯，不要因为在一起久了就忽略仪式感。', '恋爱', '浪漫,惊喜,仪式感,约会'),

-- 已婚状态
('婚姻中家务分工要公平合理。建议根据各自的时间和擅长领域来分配家务，不要认为某些家务是某一方的责任。定期沟通家务安排，及时调整。互相体谅对方的辛苦，多说感谢的话。', '已婚', '家务,分工,公平,体谅'),
('处理婆媳关系的关键在于丈夫的态度。丈夫要在母亲和妻子之间做好桥梁，不要偏袒任何一方。尊重婆婆的同时也要维护妻子的立场。遇到矛盾时，丈夫要主动沟通协调，避免让妻子独自面对。', '已婚', '婆媳关系,丈夫,家庭矛盾,沟通协调'),
('已婚夫妻要保持感情新鲜感。即使工作再忙，也要抽出时间约会和交流。可以尝试一起运动、旅行、学习新技能。不要因为生活琐事忽略了彼此的感受，定期安排二人世界很重要。', '已婚', '感情,新鲜感,约会,二人世界'),
('婚姻中的财务管理要透明。建议夫妻共同制定家庭预算，大额支出要商量决定。可以设立共同账户用于家庭开支，同时保留各自的个人账户。经济独立和共同承担要找到平衡点。', '已婚', '财务,管理,预算,共同账户'),
('育儿理念的分歧是已婚夫妻常见的问题。建议在育儿问题上达成一致，不要在孩子面前争吵。可以一起学习科学的育儿知识，尊重对方的育儿方式。遇到分歧时私下沟通，保持教育的一致性。', '已婚', '育儿,教育理念,分歧,沟通');


INSERT INTO love_knowledge (content,status, tags) VALUES
    ('已婚夫妻要保持感情新鲜感。即使工作再忙，也要抽出时间约会和交流。可以尝试一起运动、旅行、学习新技能。不要因为生活琐事忽略了彼此的感受，定期安排二人世界很重要。', '已婚','感情,新鲜感,约会,二人世界,亲密,疏远,关系');

-- ============================================================================
-- 用户体系扩展：VIP 兑换码表 + 初始管理员账号
-- （如需单独执行，可直接复制本段）
-- ============================================================================

-- VIP 兑换码表
create table if not exists vip_code
(
    id            bigint auto_increment comment 'id' primary key,
    code          varchar(64)  not null comment '兑换码',
    duration_days int          not null comment '兑换后会员时长（天）',
    is_used       tinyint      default 0 not null comment '是否已使用：0-未使用 1-已使用',
    used_by       bigint       null comment '使用人用户 id',
    used_at       datetime     null comment '使用时间',
    created_by    bigint       null comment '创建人用户 id（管理员）',
    create_time   datetime     default CURRENT_TIMESTAMP comment '创建时间',
    UNIQUE KEY uk_code (code)
) comment 'VIP 兑换码' collate = utf8mb4_unicode_ci;

-- 初始管理员账号：admin / admin123456（BCrypt 加密，首次登录后请尽快修改密码）
-- 账号已存在时自动跳过，不会覆盖已有账号
insert ignore into user (userAccount, userPassword, userName, userRole)
values ('admin', '$2a$10$BUSJLRttY7xbW4Z4JRabPOYSXucGGTVwamIiEMIXWDy69GvAS6GpW', '管理员', 'admin');

-- ============================================================================
-- 用户体系扩展：邮箱验证 + 找回密码
-- （如需单独执行，可直接复制本段）
-- ============================================================================

-- user 表补充 email 列与唯一索引（幂等：列已存在则跳过）
drop procedure if exists add_user_email_column;
delimiter //
create procedure add_user_email_column()
begin
    if not exists (
        select 1 from information_schema.columns
        where table_schema = database() and table_name = 'user' and column_name = 'email'
    ) then
        alter table user add column email varchar(128) null comment '邮箱' after userProfile;
        alter table user add unique key uk_email (email);
    end if;
end //
delimiter ;
call add_user_email_column();
drop procedure if exists add_user_email_column;

-- 邮箱验证码表
create table if not exists email_verify_code
(
    id          bigint auto_increment comment 'id' primary key,
    email       varchar(128) not null comment '邮箱',
    code        varchar(8)   not null comment '6 位验证码',
    purpose     varchar(16)  not null comment '用途：bind-绑定邮箱 reset-找回密码',
    expire_time datetime     not null comment '过期时间',
    is_used     tinyint      default 0 not null comment '是否已使用：0-未使用 1-已使用',
    create_time datetime     default CURRENT_TIMESTAMP comment '创建时间',
    index idx_email_purpose (email, purpose)
) comment '邮箱验证码' collate = utf8mb4_unicode_ci;

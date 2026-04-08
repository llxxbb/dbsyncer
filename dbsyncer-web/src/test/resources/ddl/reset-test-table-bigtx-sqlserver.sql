-- 重置测试表结构脚本 (SQL Server 大事务优化测试)
-- 用于在测试之间恢复表结构到初始状态，确保测试间的隔离性
-- 适用于：Microsoft SQL Server (Change Tracking 大事务优化场景)
-- 注意：此脚本会同时应用到源数据库和目标数据库

-- 删除并重建源表 test_bigtx_source（用于大事务优化集成测试）
IF OBJECT_ID('test_bigtx_source', 'U') IS NOT NULL 
    DROP TABLE test_bigtx_source;

CREATE TABLE test_bigtx_source (
    id INT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(100) NOT NULL,
    age INT,
    gender NVARCHAR(10),
    create_time DATETIME2 DEFAULT GETDATE()
);

-- 删除并重建目标表 test_bigtx_target（用于大事务优化集成测试）
IF OBJECT_ID('test_bigtx_target', 'U') IS NOT NULL 
    DROP TABLE test_bigtx_target;

CREATE TABLE test_bigtx_target (
    id INT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(100) NOT NULL,
    age INT,
    gender NVARCHAR(10),
    create_time DATETIME2 DEFAULT GETDATE()
);

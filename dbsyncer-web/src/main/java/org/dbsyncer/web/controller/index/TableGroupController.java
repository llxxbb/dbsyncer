package org.dbsyncer.web.controller.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dbsyncer.biz.MappingService;
import org.dbsyncer.biz.TableGroupService;
import org.dbsyncer.biz.vo.RestResult;
import org.dbsyncer.common.model.Result;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.base.ConnectorFactory;
import org.dbsyncer.parser.ParserComponent;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.model.Connector;
import org.dbsyncer.parser.model.Mapping;
import org.dbsyncer.parser.model.TableGroup;
import org.dbsyncer.sdk.SdkException;
import org.dbsyncer.sdk.config.DDLConfig;
import org.dbsyncer.sdk.connector.ConnectorInstance;
import org.dbsyncer.sdk.connector.database.AbstractDatabaseConnector;
import org.dbsyncer.sdk.connector.database.AbstractDatabaseConnector;
import org.dbsyncer.sdk.connector.database.sql.SqlTemplate;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.model.MetaInfo;
import org.dbsyncer.sdk.schema.SchemaResolver;
import org.dbsyncer.sdk.spi.ConnectorService;
import org.dbsyncer.web.controller.BaseController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import org.dbsyncer.sdk.util.PrimaryKeyUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/tableGroup")
public class TableGroupController extends BaseController {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private TableGroupService tableGroupService;

    @Resource
    private MappingService mappingService;

    @Resource
    private ConnectorFactory connectorFactory;

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private ParserComponent parserComponent;


    @GetMapping("/page/{page}")
    public String page(ModelMap model, @PathVariable("page") String page, @RequestParam(value = "id") String id) throws Exception {
        TableGroup tableGroup = tableGroupService.getTableGroup(id);
        model.put("tableGroup", tableGroup);
        String mappingId = tableGroup.getMappingId();
        model.put("mapping", mappingService.getMapping(mappingId));
        initConfig(model);
        return "mapping/" + page;
    }

    @PostMapping(value = "/add")
    @ResponseBody
    public RestResult add(HttpServletRequest request) {
        try {
            Map<String, String> params = getParams(request);
            return RestResult.restSuccess(tableGroupService.add(params));
        } catch (SdkException e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage(), 400);
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    @PostMapping(value = "/edit")
    @ResponseBody
    public RestResult edit(HttpServletRequest request) {
        try {
            Map<String, String> params = getParams(request);
            return RestResult.restSuccess(tableGroupService.edit(params));
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    @PostMapping(value = "/refreshFields")
    @ResponseBody
    public RestResult refreshFields(@RequestParam(value = "id") String id) {
        try {
            return RestResult.restSuccess(tableGroupService.refreshFields(id));
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    @PostMapping("/remove")
    @ResponseBody
    public RestResult remove(@RequestParam(value = "mappingId") String mappingId, @RequestParam(value = "ids") String ids) {
        try {
            return RestResult.restSuccess(tableGroupService.remove(mappingId, ids));
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    @PostMapping("/resetTableGroups")
    @ResponseBody
    public RestResult resetTableGroups(
            @RequestParam(value = "mappingId") String mappingId,
            @RequestParam(value = "tableGroupIds") String tableGroupIds,
            @RequestParam(value = "truncateTarget", defaultValue = "false") boolean truncateTarget) {
        try {
            return RestResult.restSuccess(tableGroupService.resetTableGroups(mappingId, tableGroupIds, truncateTarget));
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    @PostMapping(value = "/createTargetTable")
    @ResponseBody
    public RestResult createTargetTable(HttpServletRequest request) {
        try {
            Map<String, String> params = getParams(request);
            String mappingId = params.get("mappingId");
            String sourceTable = params.get("sourceTable");
            String targetTable = params.get("targetTable");

            Assert.hasText(mappingId, "mappingId 不能为空");
            Assert.hasText(sourceTable, "sourceTable 不能为空");
            Assert.hasText(targetTable, "targetTable 不能为空");

            Mapping mapping = profileComponent.getMapping(mappingId);
            Assert.notNull(mapping, "Mapping 不存在: " + mappingId);

            // 获取连接器配置
            Connector sourceConnector = profileComponent.getConnector(mapping.getSourceConnectorId());
            Connector targetConnector = profileComponent.getConnector(mapping.getTargetConnectorId());
            Assert.notNull(sourceConnector, "源连接器不存在: " + mapping.getSourceConnectorId());
            Assert.notNull(targetConnector, "目标连接器不存在: " + mapping.getTargetConnectorId());

            // 连接源和目标数据库
            ConnectorInstance sourceConnectorInstance = connectorFactory.connect(sourceConnector.getConfig());
            ConnectorInstance targetConnectorInstance = connectorFactory.connect(targetConnector.getConfig());

            // 检查目标表是否已存在（避免重复创建）
            try {
                MetaInfo existingTable = connectorFactory.getMetaInfo(targetConnectorInstance, targetTable);
                if (existingTable != null && existingTable.getColumn() != null && !existingTable.getColumn().isEmpty()) {
                    return RestResult.restSuccess("目标表已存在，无需创建");
                }
            } catch (Exception e) {
                // 表不存在，继续创建流程
                logger.debug("目标表不存在，开始创建: {}", targetTable);
            }

            // 获取源表结构
            MetaInfo sourceMetaInfo = connectorFactory.getMetaInfo(sourceConnectorInstance, sourceTable);
            Assert.notNull(sourceMetaInfo, "无法获取源表结构: " + sourceTable);
            Assert.notEmpty(sourceMetaInfo.getColumn(), "源表没有字段: " + sourceTable);

            // 判断是否为同类型数据库
            String sourceType = sourceConnector.getConfig().getConnectorType();
            String targetType = targetConnector.getConfig().getConnectorType();
            boolean isSameType = sourceType.equals(targetType);

            String createTableDDL;

            if (isSameType) {
                // 同类型数据库：直接复用源表元数据
                logger.debug("检测到同类型数据库（{}），使用优化路径", sourceType);
                
                // 提取字段和主键
                List<Field> fields = sourceMetaInfo.getColumn();
                List<String> primaryKeys;
                
                // 优先使用用户指定的主键配置，否则从字段中提取
                String targetTablePK = params.get("targetTablePK");
                if (StringUtil.isNotBlank(targetTablePK)) {
                    // 使用用户指定的主键顺序
                    primaryKeys = new ArrayList<>();
                    for (String pk : StringUtil.split(targetTablePK, StringUtil.COMMA)) {
                        primaryKeys.add(pk.trim());
                    }
                } else {
                    // 从字段中提取（按字段在列表中的顺序）
                    primaryKeys = new ArrayList<>();
                    for (Field field : fields) {
                        if (field.isPk()) {
                            primaryKeys.add(field.getName());
                        }
                    }
                }

                // 获取目标连接器的 SqlTemplate
                AbstractDatabaseConnector targetDatabaseConnector = 
                    (AbstractDatabaseConnector) connectorFactory.getConnectorService(targetType);
                SqlTemplate sqlTemplate = targetDatabaseConnector.getSqlTemplate();
                
                // 直接调用 buildCreateTableSql，跳过类型转换
                createTableDDL = sqlTemplate.buildCreateTableSql(null, targetTable, fields, primaryKeys);
                logger.debug("使用优化路径生成 CREATE TABLE DDL（跳过类型转换）");
            } else {
                // 不同类型数据库：走原有转换流程
                logger.debug("检测到不同类型数据库（{} -> {}），使用标准转换流程", sourceType, targetType);

                // 将源表的字段类型转换为标准类型（重要：避免类型污染）
                // 因为 sourceMetaInfo 中的字段类型是源数据库特定类型（如 SQL Server 的 NVARCHAR），
                // 需要先转换为标准类型，然后目标数据库的 SqlTemplate 才能正确转换为目标数据库类型
                ConnectorService sourceConnectorService = connectorFactory.getConnectorService(sourceType);
                ConnectorService targetConnectorService = connectorFactory.getConnectorService(targetType);
                SchemaResolver sourceSchemaResolver = sourceConnectorService.getSchemaResolver();

                // 创建标准化的 MetaInfo
                MetaInfo standardizedMetaInfo = new MetaInfo();
                standardizedMetaInfo.setTableType(sourceMetaInfo.getTableType());
                standardizedMetaInfo.setSql(sourceMetaInfo.getSql());
                standardizedMetaInfo.setIndexType(sourceMetaInfo.getIndexType());

                // 将源字段转换为标准类型（toStandardType 会自动保留所有元数据属性）
                List<Field> standardizedFields = new ArrayList<>();
                for (Field sourceField : sourceMetaInfo.getColumn()) {
                    Field standardField = sourceSchemaResolver.toStandardType(sourceField);
                    standardizedFields.add(standardField);
                }
                standardizedMetaInfo.setColumn(standardizedFields);

                // 生成 CREATE TABLE DDL（使用标准化后的 MetaInfo）
                // 优先使用用户指定的主键配置
                String targetTablePK = params.get("targetTablePK");
                if (targetConnectorService instanceof org.dbsyncer.connector.mysql.MySQLConnector) {
                    createTableDDL = ((org.dbsyncer.connector.mysql.MySQLConnector) targetConnectorService)
                            .generateCreateTableDDL(standardizedMetaInfo, targetTable, targetTablePK);
                } else if (targetConnectorService instanceof org.dbsyncer.connector.sqlserver.SqlServerConnector) {
                    createTableDDL = ((org.dbsyncer.connector.sqlserver.SqlServerConnector) targetConnectorService)
                            .generateCreateTableDDL(standardizedMetaInfo, targetTable, targetTablePK);
                } else if (targetConnectorService instanceof org.dbsyncer.connector.oracle.OracleConnector) {
                    createTableDDL = ((org.dbsyncer.connector.oracle.OracleConnector) targetConnectorService)
                            .generateCreateTableDDL(standardizedMetaInfo, targetTable, targetTablePK);
                } else if (targetConnectorService instanceof org.dbsyncer.connector.postgresql.PostgreSQLConnector) {
                    createTableDDL = ((org.dbsyncer.connector.postgresql.PostgreSQLConnector) targetConnectorService)
                            .generateCreateTableDDL(standardizedMetaInfo, targetTable, targetTablePK);
                } else {
                    createTableDDL = targetConnectorService.generateCreateTableDDL(standardizedMetaInfo, targetTable);
                }
            }

            // 检查连接器是否支持生成 CREATE TABLE DDL
            try {
                Assert.hasText(createTableDDL, "无法生成 CREATE TABLE DDL");

                // 执行 CREATE TABLE DDL
                DDLConfig ddlConfig = new DDLConfig();
                ddlConfig.setSql(createTableDDL);
                Result result = connectorFactory.writerDDL(targetConnectorInstance, ddlConfig, null);

                if (StringUtil.isNotBlank(result.error)) {
                    logger.error("创建表失败: {}", result.error);
                    return RestResult.restFail("创建表失败: " + result.error, 500);
                }

                logger.info("成功创建目标表: {}", targetTable);
                return RestResult.restSuccess("创建表成功");

            } catch (UnsupportedOperationException e) {
                logger.error("建表失败： {}", targetConnector.getConfig().getConnectorType(), e);
                return RestResult.restFail("建表失败：: " + e.getMessage());
            }

        } catch (IllegalArgumentException e) {
            logger.error("参数错误: {}", e.getMessage());
            return RestResult.restFail("参数错误: " + e.getMessage(), 400);
        } catch (Exception e) {
            logger.error("创建表异常: {}", e.getMessage(), e);
            return RestResult.restFail("创建表失败: " + e.getMessage(), 500);
        }
    }

    /**
     * 获取单个表映射关系的字段差异
     * <p>
     * 比较源表和目标表的字段结构，返回字段差异信息，包括：
     * <ul>
     *   <li>新增字段：目标表存在但源表不存在的字段</li>
     *   <li>缺失字段：源表存在但目标表不存在的字段</li>
     *   <li>类型不匹配：字段名相同但数据类型不同的字段</li>
     *   <li>长度不匹配：字段名相同但长度/精度不同的字段</li>
     * </ul>
     *
     * @param id 表映射关系ID（TableGroup ID）
     * @return 字段差异信息
     */
    @PostMapping(value = "/fieldDifference")
    @ResponseBody
    public RestResult getFieldDifference(@RequestParam(value = "id") String id) {
        try {
            return RestResult.restSuccess(tableGroupService.getFieldDifference(id));
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    /**
     * 批量获取多个表映射关系的字段差异
     * <p>
     * 用于批量检查多个表映射关系是否存在字段差异，返回每个ID对应的差异状态（是否有差异）。
     * 适用于列表页快速展示哪些表映射关系存在结构差异。
     *
     * @param ids 表映射关系ID列表，逗号分隔（如："id1,id2,id3"）
     * @return Map&lt;表映射关系ID, 是否有差异&gt;，true表示存在字段差异
     */
    @PostMapping(value = "/fieldDifferenceBatch")
    @ResponseBody
    public RestResult getFieldDifferenceBatch(@RequestParam(value = "ids") String ids) {
        try {
            List<String> idList = Arrays.asList(ids.split(","));
            Map<String, Boolean> result = new HashMap<>();

            for (String id : idList) {
                if (StringUtil.isNotBlank(id)) {
                    result.put(id, tableGroupService.getFieldDifference(id).isHasDifference());
                }
            }

            return RestResult.restSuccess(result);
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    /**
     * 获取字段差异修复的SQL预览
     * <p>
     * 根据检测到的字段差异，生成对应的DDL修复语句预览，包括：
     * <ul>
     *   <li>ADD COLUMN：添加缺失字段</li>
     *   <li>DROP COLUMN：删除多余字段</li>
     *   <li>MODIFY COLUMN：修改字段类型或长度</li>
     * </ul>
     * <p>
     * 修复方向：以源表为基准，修复目标表结构（使目标表与源表一致）
     *
     * @param id 表映射关系ID（TableGroup ID）
     * @return 修复预览信息 ，包含生成的SQL语句列表和警告信息
     */
    @PostMapping(value = "/fieldDiffFixPreview")
    @ResponseBody
    public RestResult getFieldDiffFixPreview(@RequestParam(value = "id") String id) {
        try {
            return RestResult.restSuccess(tableGroupService.getFieldDiffFixPreview(id));
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    /**
     * 执行字段差异修复（支持选择性修复）
     * <p>
     * 根据预览生成的DDL语句，实际执行数据库结构修复操作。
     * 支持全量修复或选择性修复部分差异项。
     * <p>
     * 执行流程：
     * <ol>
     *   <li>验证驱动状态（不能处于运行中）</li>
     *   <li>获取修复预览（复用预览逻辑生成SQL）</li>
     *   <li>根据selectedIds过滤需要执行的修复项（为空则执行全部）</li>
     *   <li>逐个执行DDL语句</li>
     *   <li>刷新表字段元数据并更新配置</li>
     * </ol>
     * <p>
     * 注意事项：
     * <ul>
     *   <li>DROP COLUMN 操作会导致数据丢失，请谨慎操作</li>
     *   <li>DDL 操作不可逆，执行前请确认预览的SQL语句</li>
     *   <li>只支持数据库类型的连接器</li>
     * </ul>
     *
     * @param id          表映射关系ID（TableGroup ID）
     * @param selectedIds 选中的差异项ID列表（JSON格式字符串），为空表示执行全部修复项
     * @return 执行结果消息，包含成功/失败的DDL语句数量
     */
    @PostMapping(value = "/executeFieldDiffFixSelective")
    @ResponseBody
    public RestResult executeFieldDiffFixSelective(
            @RequestParam(value = "id") String id,
            @RequestParam(value = "selectedIds", required = false) String selectedIds) {
        try {
            List<String> ids = null;
            if (StringUtil.isNotBlank(selectedIds)) {
                ObjectMapper objectMapper = new ObjectMapper();
                ids = objectMapper.readValue(selectedIds, new TypeReference<List<String>>() {});
            }
            return RestResult.restSuccess(tableGroupService.executeFieldDiffFix(id, ids));
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

}
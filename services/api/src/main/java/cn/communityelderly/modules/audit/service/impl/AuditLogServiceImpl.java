package cn.communityelderly.modules.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.api.PageResult;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.common.trace.TraceFilter;
import cn.communityelderly.modules.audit.dto.*;
import cn.communityelderly.modules.audit.entity.AuditLog;
import cn.communityelderly.modules.audit.mapper.AuditLogMapper;
import cn.communityelderly.modules.audit.service.AuditLogService;
import cn.communityelderly.modules.auth.entity.UserAccount;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String HMAC_SECRET = "CommunityElderlyAuditHmacSecretKey2026!";

    private final AuditLogMapper auditLogMapper;
    private final UserAccountMapper userAccountMapper;
    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public AuditLogServiceImpl(AuditLogMapper auditLogMapper,
                               UserAccountMapper userAccountMapper,
                               ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.userAccountMapper = userAccountMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public AuditLog recordAudit(Long actorUserId, String actorRole, Long communityId, String targetType, String targetId, String action, String result, String clientIp, String detailsJson) {
        AuditLog log = new AuditLog();
        log.setActorUserId(actorUserId != null ? actorUserId : 0L);
        log.setActorRole(actorRole != null ? actorRole : "SYSTEM");
        log.setCommunityId(communityId);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setAction(action);
        log.setResult(result != null ? result : "SUCCESS");
        log.setClientIp(clientIp != null ? clientIp : "127.0.0.1");
        log.setDetailsJson(detailsJson);
        log.setOccurredAt(LocalDateTime.now());

        String traceId = MDC.get(TraceFilter.MDC_KEY);
        if (!StringUtils.hasText(traceId)) {
            traceId = "trace-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        log.setTraceId(traceId);

        auditLogMapper.insert(log);
        return log;
    }

    @Override
    public PageResult<AuditLogVO> queryAuditLogs(AuditLogQueryDTO queryDTO) {
        int page = queryDTO.getPage();
        int size = queryDTO.getSize();

        CurrentUser current = UserContext.get();
        Long scopedCommunityId = null;
        if (current != null && StringUtils.hasText(current.getCommunityId())) {
            try {
                scopedCommunityId = Long.parseLong(current.getCommunityId());
            } catch (NumberFormatException ignored) {}
        }

        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (scopedCommunityId != null) {
            final Long cid = scopedCommunityId;
            wrapper.and(w -> w.eq(AuditLog::getCommunityId, cid).or().isNull(AuditLog::getCommunityId));
        }
        if (StringUtils.hasText(queryDTO.getTargetType())) {
            wrapper.eq(AuditLog::getTargetType, queryDTO.getTargetType().trim());
        }
        if (StringUtils.hasText(queryDTO.getAction())) {
            wrapper.like(AuditLog::getAction, queryDTO.getAction().trim());
        }
        if (StringUtils.hasText(queryDTO.getActorRole())) {
            wrapper.eq(AuditLog::getActorRole, queryDTO.getActorRole().trim());
        }
        if (StringUtils.hasText(queryDTO.getTraceId())) {
            wrapper.eq(AuditLog::getTraceId, queryDTO.getTraceId().trim());
        }
        wrapper.orderByDesc(AuditLog::getOccurredAt);

        Page<AuditLog> p = new Page<>(page, size);
        Page<AuditLog> resultPage = auditLogMapper.selectPage(p, wrapper);

        List<AuditLogVO> voList = new ArrayList<>();
        Map<Long, String> actorNameCache = new HashMap<>();

        for (AuditLog entity : resultPage.getRecords()) {
            AuditLogVO vo = new AuditLogVO();
            vo.setAuditId(entity.getAuditId());
            vo.setOccurredAt(entity.getOccurredAt() != null ? entity.getOccurredAt().format(DATE_TIME_FORMATTER) : "");
            vo.setActorUserId(entity.getActorUserId());
            vo.setActorRole(entity.getActorRole());
            vo.setCommunityId(entity.getCommunityId());
            vo.setTargetType(entity.getTargetType());
            vo.setTargetId(entity.getTargetId());
            vo.setAction(entity.getAction());
            vo.setActionName(formatActionName(entity.getAction()));
            vo.setResult(entity.getResult());
            vo.setClientIp(entity.getClientIp());
            vo.setTraceId(entity.getTraceId());
            vo.setDetailsJson(entity.getDetailsJson());

            // 查找操作人名称
            String actorName;
            if (entity.getActorUserId() == null || entity.getActorUserId() == 0L) {
                actorName = "系统自动化任务";
            } else {
                actorName = actorNameCache.computeIfAbsent(entity.getActorUserId(), id -> {
                    UserAccount user = userAccountMapper.selectById(id);
                    if (user != null && StringUtils.hasText(user.getPhoneNormalized())) {
                        return "操作人 (" + user.getPhoneNormalized() + ")";
                    }
                    return "操作人 (ID: " + id + ")";
                });
            }
            vo.setActorName(actorName);
            voList.add(vo);
        }

        return PageResult.of(voList, resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
    }

    @Override
    public AuditDiffVO getAuditDiff(Long auditId) {
        AuditLog log = auditLogMapper.selectById(auditId);
        if (log == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "审计日志不存在: " + auditId);
        }

        AuditDiffVO vo = new AuditDiffVO();
        vo.setAuditId(log.getAuditId());
        vo.setOccurredAt(log.getOccurredAt() != null ? log.getOccurredAt().format(DATE_TIME_FORMATTER) : "");
        vo.setActorRole(log.getActorRole());
        vo.setClientIp(log.getClientIp());
        vo.setTraceId(log.getTraceId());

        if (log.getActorUserId() == null || log.getActorUserId() == 0L) {
            vo.setActorName("系统自动化");
        } else {
            UserAccount user = userAccountMapper.selectById(log.getActorUserId());
            if (user != null && StringUtils.hasText(user.getPhoneNormalized())) {
                vo.setActorName("操作责任人 (" + user.getPhoneNormalized() + ")");
            } else {
                vo.setActorName("操作责任人 (ID: " + log.getActorUserId() + ")");
            }
        }

        // 防篡改 HMAC-SHA256 数字指纹签名
        String signatureSource = log.getAuditId() + "|" + log.getTraceId() + "|" + log.getActorRole() + "|"
                + log.getTargetType() + "|" + log.getTargetId() + "|" + log.getAction() + "|"
                + (log.getDetailsJson() != null ? log.getDetailsJson() : "") + "|" + log.getOccurredAt();
        vo.setShaSignature(calculateHmacSha256(signatureSource));

        // 解析 JSON 快照和计算 Diff
        List<AuditDiffItemVO> diffItems = new ArrayList<>();
        Object beforeJson = null;
        Object afterJson = null;

        if (StringUtils.hasText(log.getDetailsJson())) {
            try {
                JsonNode rootNode = objectMapper.readTree(log.getDetailsJson());
                if (rootNode.has("before_json") || rootNode.has("after_json")) {
                    JsonNode beforeNode = rootNode.get("before_json");
                    JsonNode afterNode = rootNode.get("after_json");

                    beforeJson = beforeNode != null && !beforeNode.isNull() ? objectMapper.treeToValue(beforeNode, Object.class) : Collections.emptyMap();
                    afterJson = afterNode != null && !afterNode.isNull() ? objectMapper.treeToValue(afterNode, Object.class) : Collections.emptyMap();

                    // 对比键值差异
                    Set<String> allKeys = new LinkedHashSet<>();
                    if (beforeNode != null && beforeNode.isObject()) {
                        beforeNode.fieldNames().forEachRemaining(allKeys::add);
                    }
                    if (afterNode != null && afterNode.isObject()) {
                        afterNode.fieldNames().forEachRemaining(allKeys::add);
                    }

                    for (String key : allKeys) {
                        String beforeVal = beforeNode != null && beforeNode.has(key) ? beforeNode.get(key).asText() : null;
                        String afterVal = afterNode != null && afterNode.has(key) ? afterNode.get(key).asText() : null;
                        if (!Objects.equals(beforeVal, afterVal)) {
                            diffItems.add(new AuditDiffItemVO(key, beforeVal, afterVal));
                        }
                    }
                } else {
                    // 平铺 JSON 结构
                    Map<String, Object> map = objectMapper.readValue(log.getDetailsJson(), new TypeReference<Map<String, Object>>() {});
                    afterJson = map;
                    beforeJson = Collections.emptyMap();
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        diffItems.add(new AuditDiffItemVO(entry.getKey(), "(初始)", String.valueOf(entry.getValue())));
                    }
                }
            } catch (Exception e) {
                // 如果解析异常，保留原始字符串
                afterJson = Collections.singletonMap("raw", log.getDetailsJson());
                beforeJson = Collections.emptyMap();
                diffItems.add(new AuditDiffItemVO("rawDetails", "(空)", log.getDetailsJson()));
            }
        } else {
            beforeJson = Collections.emptyMap();
            afterJson = Collections.emptyMap();
        }

        vo.setBeforeJson(beforeJson);
        vo.setAfterJson(afterJson);
        vo.setDiffItems(diffItems);

        return vo;
    }

    @Override
    public String exportAuditLogsAsCsv(AuditLogQueryDTO queryDTO) {
        // 最多导出 1000 条以保护内存与数据库
        queryDTO.setSize(1000);
        queryDTO.setPage(1);
        PageResult<AuditLogVO> pageResult = queryAuditLogs(queryDTO);

        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM，防止 Windows Excel 打开乱码
        sb.append('\uFEFF');
        sb.append("流水号,发生时间,操作责任人,角色,对象类型,对象ID,动作代码,动作说明,执行结果,客户端IP,Trace-Id\n");

        for (AuditLogVO vo : pageResult.getItems()) {
            sb.append(escapeCsv(String.valueOf(vo.getAuditId()))).append(",");
            sb.append(escapeCsv(vo.getOccurredAt())).append(",");
            sb.append(escapeCsv(vo.getActorName())).append(",");
            sb.append(escapeCsv(vo.getActorRole())).append(",");
            sb.append(escapeCsv(vo.getTargetType())).append(",");
            sb.append(escapeCsv(vo.getTargetId())).append(",");
            sb.append(escapeCsv(vo.getAction())).append(",");
            sb.append(escapeCsv(vo.getActionName())).append(",");
            sb.append(escapeCsv(vo.getResult())).append(",");
            sb.append(escapeCsv(vo.getClientIp())).append(",");
            sb.append(escapeCsv(vo.getTraceId())).append("\n");
        }

        return sb.toString();
    }

    private String escapeCsv(String val) {
        if (val == null) {
            return "";
        }
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private String calculateHmacSha256(String input) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();
        } catch (Exception e) {
            return "HMAC_CALCULATION_ERROR";
        }
    }

    private String formatActionName(String action) {
        if (!StringUtils.hasText(action)) {
            return "未知操作";
        }
        switch (action) {
            case "APPROVE_RESERVATION":
                return "服务预约审核通过并派单";
            case "REJECT_CONFLICT":
                return "重叠时隙防冲突自动阻断";
            case "FREEZE":
                return "争议维权紧急冻结";
            case "UNFREEZE":
                return "争议维权核实解除冻结";
            case "WORK_ORDER_APPROVE":
                return "助老工单审核结案归档";
            case "WORK_ORDER_REJECT":
                return "助老工单审核退回重办";
            case "EXEMPTION_APPROVE":
                return "免照豁免审批结案";
            case "UPDATE_VERSION":
                return "用药方案版本升版";
            case "CANCEL_FALSE_TOUCH":
                return "SOS误触长按带痕取消";
            case "APPLY_CORRECTION":
                return "长辈档案更正申请提交";
            case "APPROVE_CORRECTION":
                return "长辈档案更正审批通过";
            default:
                return action;
        }
    }
}

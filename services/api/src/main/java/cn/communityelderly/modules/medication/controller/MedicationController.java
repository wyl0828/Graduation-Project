package cn.communityelderly.modules.medication.controller;

import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.idempotency.Idempotency;
import cn.communityelderly.common.security.RequireRoles;
import cn.communityelderly.modules.medication.dto.*;
import cn.communityelderly.modules.medication.service.MedicationComplianceService;
import cn.communityelderly.modules.medication.service.MedicationPlanService;
import cn.communityelderly.modules.medication.service.MedicationTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/medication")
public class MedicationController {

    private final MedicationPlanService planService;
    private final MedicationTaskService taskService;
    private final MedicationComplianceService complianceService;

    public MedicationController(MedicationPlanService planService,
                                MedicationTaskService taskService,
                                MedicationComplianceService complianceService) {
        this.planService = planService;
        this.taskService = taskService;
        this.complianceService = complianceService;
    }

    // 1. 创建不可变用药计划 (P-19)
    @PostMapping("/plans")
    @RequireRoles({"CHILD"})
    public Result<MedicationPlanVO> createPlan(@Valid @RequestBody MedicationPlanCreateDTO dto) {
        return Result.success(planService.createPlan(dto));
    }

    // 2. 调整用药计划 (版本不可逆覆盖 + 当日应急刷新)
    @PutMapping("/plans/{planId}")
    @RequireRoles({"CHILD"})
    public Result<MedicationPlanVO> updatePlan(@PathVariable Long planId,
                                              @Valid @RequestBody MedicationPlanUpdateDTO dto) {
        return Result.success(planService.updatePlan(planId, dto));
    }

    // 3. 查询长辈在服计划列表
    @GetMapping("/plans/elder/{elderId}")
    @RequireRoles({"CHILD", "ELDER", "COMMUNITY_ADMIN", "GRID_WORKER"})
    public Result<List<MedicationPlanVO>> listPlans(@PathVariable Long elderId) {
        return Result.success(planService.listPlans(elderId));
    }

    // 4. 查询版本履历列表
    @GetMapping("/plans/{planId}/versions")
    @RequireRoles({"CHILD", "COMMUNITY_ADMIN", "GRID_WORKER"})
    public Result<List<MedicationPlanVersionVO>> listPlanVersions(@PathVariable Long planId) {
        return Result.success(planService.listPlanVersions(planId));
    }

    // 5. 今日老人大字药盒任务列表 (P-18)
    @GetMapping("/tasks/today/{elderId}")
    @RequireRoles({"ELDER", "CHILD", "COMMUNITY_ADMIN", "GRID_WORKER"})
    public Result<List<MedicationTaskVO>> getTodayTasks(@PathVariable Long elderId) {
        return Result.success(taskService.getTodayTasks(elderId));
    }

    // 6. 老人服药一键打卡确认 (带幂等)
    @PostMapping("/tasks/checkin")
    @Idempotency(required = false)
    @RequireRoles({"ELDER"})
    public Result<MedicationTaskVO> checkin(@Valid @RequestBody MedicationCheckinDTO dto) {
        return Result.success(taskService.checkin(dto));
    }

    // 7. 近30天服药遵从率大看板 (P-19)
    @GetMapping("/compliance/{elderId}")
    @RequireRoles({"CHILD", "COMMUNITY_ADMIN", "GRID_WORKER"})
    public Result<MedicationComplianceVO> getCompliance(@PathVariable Long elderId,
                                                        @RequestParam(required = false, defaultValue = "30") Integer days) {
        return Result.success(complianceService.getCompliance(elderId, days));
    }

    // 8. 社区今日超时未服清单 (P-21 / Web管理端)
    @GetMapping("/admin/timeout-tasks")
    @RequireRoles({"COMMUNITY_ADMIN", "GRID_WORKER"})
    public Result<List<MedicationTaskVO>> listCommunityTimeoutElders() {
        return Result.success(complianceService.listCommunityTimeoutElders());
    }

    // 9. 登记电话问候关怀留痕
    @PostMapping("/admin/tasks/{taskId}/phone-care")
    @RequireRoles({"COMMUNITY_ADMIN", "GRID_WORKER"})
    public Result<Void> recordPhoneCare(@PathVariable Long taskId,
                                        @RequestParam(required = false, defaultValue = "已致电问候并提醒服药") String careNotes) {
        complianceService.recordPhoneCare(taskId, careNotes);
        return Result.success();
    }
}

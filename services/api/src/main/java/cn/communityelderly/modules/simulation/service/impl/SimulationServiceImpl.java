package cn.communityelderly.modules.simulation.service.impl;

import cn.communityelderly.modules.health.dto.VitalRecordCreateDTO;
import cn.communityelderly.modules.health.entity.VitalRecord;
import cn.communityelderly.modules.health.mapper.VitalRecordMapper;
import cn.communityelderly.modules.health.service.VitalRecordService;
import cn.communityelderly.modules.simulation.dto.SimulationConfigDTO;
import cn.communityelderly.modules.simulation.dto.SimulationStatusVO;
import cn.communityelderly.modules.simulation.dto.SingleInjectDTO;
import cn.communityelderly.modules.simulation.entity.SimulationControl;
import cn.communityelderly.modules.simulation.mapper.SimulationControlMapper;
import cn.communityelderly.modules.simulation.service.SimulationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SimulationServiceImpl implements SimulationService {

    private final SimulationControlMapper simulationControlMapper;
    private final VitalRecordService vitalRecordService;
    private final VitalRecordMapper vitalRecordMapper;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ConcurrentHashMap<String, ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> counterMap = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public SimulationServiceImpl(SimulationControlMapper simulationControlMapper,
                                 VitalRecordService vitalRecordService,
                                 VitalRecordMapper vitalRecordMapper) {
        this.simulationControlMapper = simulationControlMapper;
        this.vitalRecordService = vitalRecordService;
        this.vitalRecordMapper = vitalRecordMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized void startSimulation(SimulationConfigDTO dto) {
        String taskKey = buildKey(dto.getElderId(), dto.getVitalType());

        // 先停止之前的任务
        stopSimulation(dto.getElderId(), dto.getVitalType());

        LocalDateTime now = LocalDateTime.now();
        SimulationControl control = new SimulationControl();
        control.setElderId(dto.getElderId());
        control.setVitalType(dto.getVitalType().toUpperCase());
        control.setStatus("RUNNING");
        control.setSampleIntervalSec(dto.getSampleIntervalSec());
        control.setBaseValuePrimary(dto.getBaseValuePrimary());
        control.setBaseValueSecondary(dto.getBaseValueSecondary());
        control.setVolatilityRange(dto.getVolatilityRange());
        control.setAbnormalInjectionRate(dto.getAbnormalInjectionRate());
        control.setStartedAt(now);
        simulationControlMapper.insert(control);

        AtomicLong counter = new AtomicLong(0);
        counterMap.put(taskKey, counter);

        // 启动定时生成调度
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                generateSimulatedPoint(dto, counter);
            } catch (Exception e) {
                // 忽略异常，保持周期调度
            }
        }, 1, dto.getSampleIntervalSec(), TimeUnit.SECONDS);

        runningTasks.put(taskKey, future);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized void stopSimulation(Long elderId, String vitalType) {
        String key = buildKey(elderId, vitalType);
        ScheduledFuture<?> future = runningTasks.remove(key);
        if (future != null) {
            future.cancel(false);
        }

        SimulationControl running = simulationControlMapper.selectOne(
                new LambdaQueryWrapper<SimulationControl>()
                        .eq(SimulationControl::getElderId, elderId)
                        .eq(SimulationControl::getVitalType, vitalType.toUpperCase())
                        .eq(SimulationControl::getStatus, "RUNNING")
                        .orderByDesc(SimulationControl::getControlId)
                        .last("LIMIT 1")
        );
        if (running != null) {
            running.setStatus("STOPPED");
            running.setStoppedAt(LocalDateTime.now());
            simulationControlMapper.updateById(running);
        }
    }

    @Override
    public Long injectSingleAbnormal(SingleInjectDTO dto) {
        VitalRecordCreateDTO createDTO = new VitalRecordCreateDTO();
        createDTO.setElderId(dto.getElderId());
        createDTO.setVitalType(dto.getVitalType().toUpperCase());
        createDTO.setValuePrimary(dto.getValuePrimary());
        createDTO.setValueSecondary(dto.getValueSecondary());
        createDTO.setUnit("BLOOD_PRESSURE".equalsIgnoreCase(dto.getVitalType()) ? "mmHg" : "mmol/L");
        createDTO.setSourceType("SIMULATED");
        createDTO.setRecordedAt(LocalDateTime.now());
        createDTO.setRemark("IoT受控仿真控制台手动单次注入超标指标 (答辩演示)");

        return vitalRecordService.recordVital(createDTO);
    }

    @Override
    public SimulationStatusVO getStatus(Long elderId, String vitalType) {
        String key = buildKey(elderId, vitalType);
        boolean isRunning = runningTasks.containsKey(key);

        SimulationControl control = simulationControlMapper.selectOne(
                new LambdaQueryWrapper<SimulationControl>()
                        .eq(SimulationControl::getElderId, elderId)
                        .eq(SimulationControl::getVitalType, vitalType.toUpperCase())
                        .orderByDesc(SimulationControl::getControlId)
                        .last("LIMIT 1")
        );

        Long simulatedCount = vitalRecordMapper.selectCount(
                new LambdaQueryWrapper<VitalRecord>()
                        .eq(VitalRecord::getElderId, elderId)
                        .eq(VitalRecord::getVitalType, vitalType.toUpperCase())
                        .eq(VitalRecord::getSourceType, "SIMULATED")
        );

        VitalRecord lastRecord = vitalRecordMapper.selectOne(
                new LambdaQueryWrapper<VitalRecord>()
                        .eq(VitalRecord::getElderId, elderId)
                        .eq(VitalRecord::getVitalType, vitalType.toUpperCase())
                        .eq(VitalRecord::getSourceType, "SIMULATED")
                        .orderByDesc(VitalRecord::getRecordedAt)
                        .last("LIMIT 1")
        );

        SimulationStatusVO vo = new SimulationStatusVO();
        vo.setStatus(isRunning ? "RUNNING" : "STOPPED");
        vo.setElderId(elderId);
        vo.setVitalType(vitalType.toUpperCase());
        vo.setSampleIntervalSec(control != null ? control.getSampleIntervalSec() : 10);
        vo.setGeneratedCount(simulatedCount != null ? simulatedCount : 0L);
        vo.setStartedAt(control != null ? control.getStartedAt() : null);
        vo.setLastGeneratedAt(lastRecord != null ? lastRecord.getRecordedAt() : null);

        return vo;
    }

    private void generateSimulatedPoint(SimulationConfigDTO dto, AtomicLong counter) {
        double delta = (random.nextDouble() * 2 - 1) * dto.getVolatilityRange().doubleValue();
        double pVal = dto.getBaseValuePrimary().doubleValue() + delta;
        double sVal = dto.getBaseValueSecondary() != null ? dto.getBaseValueSecondary().doubleValue() + (delta * 0.6) : 0;

        // 判定是否注入异常
        if (random.nextDouble() < dto.getAbnormalInjectionRate().doubleValue()) {
            pVal += 32.0; // 注入高血压
            sVal += 18.0;
        }

        BigDecimal primary = BigDecimal.valueOf(pVal).setScale(2, RoundingMode.HALF_UP);
        BigDecimal secondary = dto.getBaseValueSecondary() != null ? BigDecimal.valueOf(sVal).setScale(2, RoundingMode.HALF_UP) : null;

        VitalRecordCreateDTO createDTO = new VitalRecordCreateDTO();
        createDTO.setElderId(dto.getElderId());
        createDTO.setVitalType(dto.getVitalType().toUpperCase());
        createDTO.setValuePrimary(primary);
        createDTO.setValueSecondary(secondary);
        createDTO.setUnit("BLOOD_PRESSURE".equalsIgnoreCase(dto.getVitalType()) ? "mmHg" : "mmol/L");
        createDTO.setSourceType("SIMULATED");
        createDTO.setRecordedAt(LocalDateTime.now());
        createDTO.setRemark("IoT设备仿真自动上报 (采样序号: #" + counter.incrementAndGet() + ")");

        vitalRecordService.recordVital(createDTO);
    }

    private String buildKey(Long elderId, String vitalType) {
        return elderId + ":" + (vitalType != null ? vitalType.toUpperCase() : "BLOOD_PRESSURE");
    }

    @PreDestroy
    public void destroy() {
        scheduler.shutdownNow();
    }
}

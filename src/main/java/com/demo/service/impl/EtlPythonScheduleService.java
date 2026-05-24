package com.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demo.entity.EtlBatchStatus;
import com.demo.entity.TradeCalendar;
import com.demo.entity.SysParam;
import com.demo.service.EtlBatchStatusService;
import com.demo.service.TradeCalendarService;
import com.demo.service.SysParamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class EtlPythonScheduleService {

    private static final Logger log = LoggerFactory.getLogger(EtlPythonScheduleService.class);
    private static final String SCHEDULE_ENABLED_KEY = "etl.schedule.enabled";
    private static final String SCHEDULE_TIME_KEY = "etl.schedule.time";
    private static final String RUN_MODE_KEY = "RUN_MODE";
    private static final String RECENT_DAYS_KEY = "RECENT_DAYS";
    private static final String INCLUDE_TODAY_KEY = "INCLUDE_TODAY";
    private static final String MAX_PROCESS_DAYS_KEY = "MAX_PROCESS_DAYS";
    private static final String PYTHON_COMMAND_KEY = "PYTHON_COMMAND";
    private static final String PYTHON_SCRIPT_PATH_KEY = "PYTHON_SCRIPT_PATH";
    private static final String AUTO_JOB_NAME = "ETF_DATA_ETL";
    private static final String MANUAL_JOB_NAME = "ETF_MANUAL_IMPORT";
    private static final String MANUAL_STATUS_REQUESTED = "REQUESTED";
    private static final String MANUAL_STATUS_RUNNING = "RUNNING";
    private static final String MANUAL_STATUS_SUCCESS = "SUCCESS";
    private static final String MANUAL_STATUS_FAILED = "FAILED";
    private static final String DEFAULT_SCHEDULE_TIME = "08:00";
    private static final String DEFAULT_PYTHON_COMMAND = "python";
    private static final String DEFAULT_PYTHON_SCRIPT_PATH = "scripts/getETFInfo_new.py";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter BATCH_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile LocalDate lastTriggeredDate;

    @Autowired
    private SysParamService sysParamService;

    @Autowired
    private EtlBatchStatusService etlBatchStatusService;

    @Autowired
    private TradeCalendarService tradeCalendarService;

    @Scheduled(cron = "0 * * * * ?")
    public void runDailyEtlIfNeeded() {
        if (!isScheduleEnabled()) {
            return;
        }

        LocalTime scheduledTime = readScheduleTime();
        LocalDate currentDate = LocalDate.now();
        LocalTime now = LocalTime.now().withSecond(0).withNano(0);
        if (!now.equals(scheduledTime)) {
            return;
        }

        if (currentDate.equals(lastTriggeredDate)) {
            return;
        }

        if (!running.compareAndSet(false, true)) {
            log.info("ETL python task is already running, skip duplicate trigger");
            return;
        }

        String batchNo = "AUTO_" + LocalDateTime.now().format(BATCH_NO_FORMATTER);
        TriggerWindow triggerWindow = resolveTriggerWindow();
        lastTriggeredDate = currentDate;
        try {
            insertTriggerRecord(batchNo, AUTO_JOB_NAME, triggerWindow.startDate, triggerWindow.endDate, MANUAL_STATUS_REQUESTED);
            updateTriggerRecord(batchNo, MANUAL_STATUS_RUNNING, null);
            executePythonScript();
            updateTriggerRecord(batchNo, MANUAL_STATUS_SUCCESS, null);
        } catch (Exception ex) {
            log.error("Failed to execute scheduled ETL python task", ex);
            updateTriggerRecord(batchNo, MANUAL_STATUS_FAILED, ex.getMessage());
        } finally {
            running.set(false);
        }
    }

    public void runPythonScriptNow() throws IOException, InterruptedException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("ETL python task is already running");
        }
        try {
            executePythonScript();
        } finally {
            running.set(false);
        }
    }

    public String triggerPythonScriptAsync() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("自动任务执行中，请等待");
        }

        String batchNo = "MANUAL_" + LocalDateTime.now().format(BATCH_NO_FORMATTER);
        TriggerWindow triggerWindow = resolveTriggerWindow();
        try {
            insertTriggerRecord(batchNo, MANUAL_JOB_NAME, triggerWindow.startDate, triggerWindow.endDate, MANUAL_STATUS_REQUESTED);
        } catch (Exception ex) {
            running.set(false);
            log.error("Failed to insert manual ETL trigger marker", ex);
            return "导入失败: " + ex.getMessage();
        }

        Thread worker = new Thread(() -> {
            try {
                updateTriggerRecord(batchNo, MANUAL_STATUS_RUNNING, null);
                executePythonScript();
                updateTriggerRecord(batchNo, MANUAL_STATUS_SUCCESS, null);
            } catch (Exception ex) {
                log.error("Failed to execute manual ETL python task", ex);
                updateTriggerRecord(batchNo, MANUAL_STATUS_FAILED, ex.getMessage());
            } finally {
                running.set(false);
            }
        }, "etl-python-manual-trigger");
        worker.start();
        return "导入中，请稍后";
    }

    private boolean isScheduleEnabled() {
        String value = readParamValue(SCHEDULE_ENABLED_KEY);
        if (value.isEmpty()) {
            return true;
        }
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    private LocalTime readScheduleTime() {
        String value = readParamValue(SCHEDULE_TIME_KEY);
        String timeText = value.isEmpty() ? DEFAULT_SCHEDULE_TIME : value;
        try {
            return LocalTime.parse(timeText, TIME_FORMATTER).withSecond(0).withNano(0);
        } catch (DateTimeParseException ex) {
            log.warn("Invalid ETL schedule time '{}', fallback to {}", timeText, DEFAULT_SCHEDULE_TIME);
            return LocalTime.parse(DEFAULT_SCHEDULE_TIME, TIME_FORMATTER);
        }
    }

    private void executePythonScript() throws IOException, InterruptedException {
        Path scriptPath = extractPythonScript();
        String pythonCommand = resolvePythonCommand();
        ProcessBuilder processBuilder = new ProcessBuilder(pythonCommand, scriptPath.toString());
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().put("PYTHONIOENCODING", "UTF-8");

        log.info("Triggering ETL python script: command={}, script={}", pythonCommand, scriptPath);
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException ex) {
            throw new IllegalStateException("Python 执行环境不可用，请检查 PYTHON_COMMAND 配置或 Python 安装: " + ex.getMessage(), ex);
        }
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[etl-python] {}", line);
                if (output.length() < 8000) {
                    output.append(line).append(System.lineSeparator());
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String tail = output.length() == 0 ? "" : ", output=\n" + output;
            throw new IllegalStateException("Python ETL script exited with code " + exitCode + tail);
        }
        log.info("Scheduled ETL python script completed successfully");
    }

    private String resolvePythonCommand() {
        String command = readParamValue(PYTHON_COMMAND_KEY);
        if (command.isEmpty()) {
            return DEFAULT_PYTHON_COMMAND;
        }
        return command;
    }

    private void insertTriggerRecord(String batchNo, String jobName, Integer tradeDateStart, Integer tradeDateEnd, String status) {
        EtlBatchStatus record = new EtlBatchStatus();
        record.setBatchNo(batchNo);
        record.setJobName(jobName);
        record.setStartTime(LocalDateTime.now());
        record.setStatus(status);
        record.setTradeDateStart(tradeDateStart);
        record.setTradeDateEnd(tradeDateEnd);
        etlBatchStatusService.save(record);
        log.info("Inserted ETL trigger marker batchNo={}, jobName={}, status={}", batchNo, jobName, status);
    }

    private void updateTriggerRecord(String batchNo, String status, String errorMessage) {
        EtlBatchStatus record = new EtlBatchStatus();
        record.setStatus(status);
        record.setEndTime(MANUAL_STATUS_RUNNING.equals(status) ? null : LocalDateTime.now());
        record.setErrorMessage(errorMessage);
        etlBatchStatusService.update(record, new LambdaUpdateWrapper<EtlBatchStatus>()
                .eq(EtlBatchStatus::getBatchNo, batchNo));
    }

    private Path extractPythonScript() throws IOException {
        String scriptLocation = readParamValue(PYTHON_SCRIPT_PATH_KEY);
        if (scriptLocation.isEmpty()) {
            scriptLocation = DEFAULT_PYTHON_SCRIPT_PATH;
        }
        ClassPathResource resource = new ClassPathResource(scriptLocation);
        if (!resource.exists()) {
            throw new IllegalStateException("Python 脚本不存在，请检查 PYTHON_SCRIPT_PATH 配置: " + scriptLocation);
        }
        Path tempFile = Files.createTempFile("getETFInfo_new-", ".py");
        tempFile.toFile().deleteOnExit();
        try (InputStream inputStream = resource.getInputStream()) {
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    private TriggerWindow resolveTriggerWindow() {
        List<Integer> openTradeDates = loadOpenTradeDates();
        if (openTradeDates.isEmpty()) {
            int todayInt = Integer.parseInt(LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
            return new TriggerWindow(todayInt, todayInt);
        }

        String runMode = readParamValue(RUN_MODE_KEY);
        if (runMode.isEmpty()) {
            runMode = "recent";
        }
        runMode = runMode.trim().toLowerCase();

        int todayInt = Integer.parseInt(LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
        boolean includeToday = isTruthy(readParamValue(INCLUDE_TODAY_KEY), false);
        int recentDays = parsePositiveInt(readParamValue(RECENT_DAYS_KEY), 1);
        int maxProcessDays = parsePositiveInt(readParamValue(MAX_PROCESS_DAYS_KEY), 30);

        List<Integer> candidateDates = new ArrayList<>(openTradeDates);
        if (!includeToday) {
            candidateDates.removeIf(date -> date == todayInt);
        }

        if (candidateDates.isEmpty()) {
            int fallback = openTradeDates.get(openTradeDates.size() - 1);
            return new TriggerWindow(fallback, fallback);
        }

        if ("full".equals(runMode)) {
            List<Integer> window = tail(candidateDates, maxProcessDays);
            return new TriggerWindow(window.get(0), window.get(window.size() - 1));
        }

        if ("recent".equals(runMode)) {
            int needDays = recentDays + (includeToday ? 0 : 1);
            List<Integer> window = tail(candidateDates, needDays);
            if (!includeToday) {
                window.removeIf(date -> date == todayInt);
            }
            if (window.isEmpty()) {
                int fallback = candidateDates.get(candidateDates.size() - 1);
                return new TriggerWindow(fallback, fallback);
            }
            return new TriggerWindow(window.get(0), window.get(window.size() - 1));
        }

        int fallback = candidateDates.get(candidateDates.size() - 1);
        return new TriggerWindow(fallback, fallback);
    }

    private List<Integer> loadOpenTradeDates() {
        List<TradeCalendar> rows = tradeCalendarService.lambdaQuery()
                .eq(TradeCalendar::getIsOpen, 1)
                .orderByAsc(TradeCalendar::getTradeDate)
                .list();
        List<Integer> tradeDates = new ArrayList<>();
        LocalDate today = LocalDate.now();
        int filteredFutureDates = 0;
        for (TradeCalendar row : rows) {
            if (row != null && row.getTradeDate() != null) {
                LocalDate tradeDate = row.getTradeDate();
                if (!tradeDate.isAfter(today)) {
                    tradeDates.add(Integer.parseInt(tradeDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)));
                } else {
                    filteredFutureDates++;
                }
            }
        }
        if (filteredFutureDates > 0) {
            log.warn("Ignored {} future trade_calendar rows after today, likely placeholder dates", filteredFutureDates);
        }
        return tradeDates;
    }

    private List<Integer> tail(List<Integer> values, int count) {
        if (values.isEmpty()) {
            return new ArrayList<>();
        }
        int size = Math.max(1, count);
        int fromIndex = Math.max(0, values.size() - size);
        return new ArrayList<>(values.subList(fromIndex, values.size()));
    }

    private int parsePositiveInt(String value, int defaultValue) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : defaultValue;
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private boolean isTruthy(String value, boolean defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        String normalized = value.trim().toLowerCase();
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized);
    }

    private String readParamValue(String key) {
        LambdaQueryWrapper<SysParam> wrapper = new LambdaQueryWrapper<SysParam>()
                .eq(SysParam::getParamKey, key)
                .eq(SysParam::getIsActive, 1)
                .orderByDesc(SysParam::getId)
                .last("LIMIT 1");
        SysParam param = sysParamService.getOne(wrapper, false);
        return param == null || param.getParamValue() == null ? "" : param.getParamValue().trim();
    }

    private static final class TriggerWindow {
        private final Integer startDate;
        private final Integer endDate;

        private TriggerWindow(Integer startDate, Integer endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }
}

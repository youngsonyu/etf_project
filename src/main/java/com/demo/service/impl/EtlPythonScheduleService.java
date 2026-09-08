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
import java.util.Arrays;
import java.util.Collections;
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
    private static final String PYTHON_COMMAND_ENV = "PYTHON_COMMAND";
    private static final String PYTHON_SCRIPT_PATH_ENV = "PYTHON_SCRIPT_PATH";
    private static final String AUTO_JOB_NAME = "ETF_DATA_ETL";
    private static final String MANUAL_JOB_NAME = "ETF_MANUAL_IMPORT";
    private static final String MANUAL_STATUS_REQUESTED = "REQUESTED";
    private static final String MANUAL_STATUS_RUNNING = "RUNNING";
    private static final String MANUAL_STATUS_SUCCESS = "SUCCESS";
    private static final String MANUAL_STATUS_FAILED = "FAILED";
    private static final String MANUAL_STATUS_PARTIAL = "PARTIAL";
    private static final String DEFAULT_SCHEDULE_TIME = "08:00";
    private static final String DEFAULT_PYTHON_COMMAND = "python";
    private static final String DEFAULT_PYTHON_SCRIPT_PATH = "scripts/getETFInfo_new.py";
    private static final String NO_WORK_HINT = "[INFO] 没有需要处理的新交易日，退出";
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
            PythonExecutionResult result = executePythonScript(Collections.emptyList());
            updateTriggerRecordAfterExecution(batchNo, result);
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
            executePythonScript(Collections.emptyList());
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
                // --import-only：只导入 K 线/PCF/份额/IOPV，不计算技术指标
                PythonExecutionResult result = executePythonScript(Collections.singletonList("--import-only"));
                updateTriggerRecordAfterExecution(batchNo, result);
            } catch (Exception ex) {
                log.error("Failed to execute manual ETL python task", ex);
                updateTriggerRecord(batchNo, MANUAL_STATUS_FAILED, ex.getMessage());
            } finally {
                running.set(false);
            }
        }, "etl-python-manual-trigger");
        worker.start();
        return "导入任务已触发（批次号：" + batchNo + "），请稍后刷新查看状态";
    }

    /**
     * 仅触发 L2 技术指标计算，复用 sys_param 中的 RUN_MODE/RECENT_DAYS/INCLUDE_TODAY/MAX_PROCESS_DAYS，
     * 与"银河证券数据导入"按钮使用同一套配置，不引入新的全量/增量开关。
     */
    public String triggerTaCalcAsync() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("自动任务执行中，请等待");
        }

        String batchNo = "TA_CALC_" + LocalDateTime.now().format(BATCH_NO_FORMATTER);
        TriggerWindow triggerWindow = resolveTriggerWindow();
        try {
            insertTriggerRecord(batchNo, MANUAL_JOB_NAME, triggerWindow.startDate, triggerWindow.endDate, MANUAL_STATUS_REQUESTED);
        } catch (Exception ex) {
            running.set(false);
            log.error("Failed to insert manual TA calc trigger marker", ex);
            return "L2指标计算失败: " + ex.getMessage();
        }

        Thread worker = new Thread(() -> {
            try {
                updateTriggerRecord(batchNo, MANUAL_STATUS_RUNNING, null);
                // 通过命令行参数 --ta-only 告知 Python 脚本：只算指标，跳过 K 线/PCF/份额/IOPV 等抽取
                PythonExecutionResult result = executePythonScript(Collections.singletonList("--ta-only"));
                updateTriggerRecordAfterExecution(batchNo, result);
            } catch (Exception ex) {
                log.error("Failed to execute manual TA calc python task", ex);
                updateTriggerRecord(batchNo, MANUAL_STATUS_FAILED, ex.getMessage());
            } finally {
                running.set(false);
            }
        }, "etl-ta-calc-manual-trigger");
        worker.start();
        return "L2指标计算任务已触发（批次号：" + batchNo + "），请稍后刷新查看状态";
    }

    /**
     * 按天对齐 L2 指标计算：每个交易日 4 周期各一条，写入 etf_ta_indicator_daily。
     * 复用 sys_param 中的 RUN_MODE/RECENT_DAYS/INCLUDE_TODAY/MAX_PROCESS_DAYS。
     */
    public String triggerTaDailyCalcAsync() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("自动任务执行中，请等待");
        }

        String batchNo = "TA_DAILY_" + LocalDateTime.now().format(BATCH_NO_FORMATTER);
        TriggerWindow triggerWindow = resolveTriggerWindow();
        try {
            insertTriggerRecord(batchNo, MANUAL_JOB_NAME, triggerWindow.startDate, triggerWindow.endDate, MANUAL_STATUS_REQUESTED);
        } catch (Exception ex) {
            running.set(false);
            log.error("Failed to insert manual TA daily calc trigger marker", ex);
            return "按天对齐L2指标计算失败: " + ex.getMessage();
        }

        Thread worker = new Thread(() -> {
            try {
                updateTriggerRecord(batchNo, MANUAL_STATUS_RUNNING, null);
                PythonExecutionResult result = executePythonScript(Collections.singletonList("--ta-daily"));
                updateTriggerRecordAfterExecution(batchNo, result);
            } catch (Exception ex) {
                log.error("Failed to execute manual TA daily calc python task", ex);
                updateTriggerRecord(batchNo, MANUAL_STATUS_FAILED, ex.getMessage());
            } finally {
                running.set(false);
            }
        }, "etl-ta-daily-manual-trigger");
        worker.start();
        return "按天对齐L2指标计算任务已触发（批次号：" + batchNo + "），请稍后刷新查看状态";
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

    private PythonExecutionResult executePythonScript(List<String> extraArgs) throws IOException, InterruptedException {
        Path scriptPath = extractPythonScript();
        List<String> pythonCommand = resolvePythonCommandParts();
        validatePythonDependencies(pythonCommand);

        List<String> command = new ArrayList<>(pythonCommand);
        command.add(scriptPath.toString());
        if (extraArgs != null && !extraArgs.isEmpty()) {
            command.addAll(extraArgs);
        }
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().put("PYTHONIOENCODING", "UTF-8");

        log.info("Triggering ETL python script: command={}, script={}", String.join(" ", command), scriptPath);
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
        boolean noWork = output.toString().contains(NO_WORK_HINT);
        return new PythonExecutionResult(noWork);
    }

    private void updateTriggerRecordAfterExecution(String batchNo, PythonExecutionResult result) {
        if (result.noWork) {
            updateTriggerRecord(batchNo, MANUAL_STATUS_PARTIAL, "没有可处理的新交易日，本次导入已跳过");
            return;
        }
        updateTriggerRecord(batchNo, MANUAL_STATUS_SUCCESS, null);
    }

    private List<String> resolvePythonCommandParts() {
        String configured = readConfigValue(PYTHON_COMMAND_KEY, PYTHON_COMMAND_ENV);
        if (!configured.isEmpty()) {
            List<String> configuredParts = splitCommand(configured);
            if (canExecutePython(configuredParts)) {
                return configuredParts;
            }
            throw new IllegalStateException("PYTHON_COMMAND 配置不可用: " + configured);
        }

        List<List<String>> candidates = Arrays.asList(
                Arrays.asList(DEFAULT_PYTHON_COMMAND),
                Arrays.asList("python3"),
                Arrays.asList("py", "-3"),
                Arrays.asList("py")
        );
        for (List<String> candidate : candidates) {
            if (canExecutePython(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException("未找到可用的 Python 命令，请安装 Python 或在 sys_param 中配置 PYTHON_COMMAND");
    }

    private void validatePythonDependencies(List<String> pythonCommandParts) {
        List<String> checkCommand = new ArrayList<>(pythonCommandParts);
        checkCommand.add("-c");
        checkCommand.add("import numpy, pandas, sqlalchemy, AmazingData");
        CommandResult result = runCommand(checkCommand);
        if (result.exitCode != 0) {
            String details = result.output.isEmpty() ? "" : (", output=\n" + result.output);
            throw new IllegalStateException("Python 依赖缺失或环境异常，请安装 numpy/pandas/sqlalchemy/AmazingData" + details);
        }
    }

    private boolean canExecutePython(List<String> commandParts) {
        List<String> checkCommand = new ArrayList<>(commandParts);
        checkCommand.add("--version");
        CommandResult result = runCommand(checkCommand);
        return result.exitCode == 0;
    }

    private CommandResult runCommand(List<String> commandParts) {
        ProcessBuilder processBuilder = new ProcessBuilder(commandParts);
        processBuilder.redirectErrorStream(true);
        StringBuilder output = new StringBuilder();
        try {
            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() < 4000) {
                        output.append(line).append(System.lineSeparator());
                    }
                }
            }
            int exitCode = process.waitFor();
            return new CommandResult(exitCode, output.toString().trim());
        } catch (Exception ex) {
            return new CommandResult(-1, ex.getMessage());
        }
    }

    private List<String> splitCommand(String command) {
        List<String> parts = new ArrayList<>();
        String trimmed = command == null ? "" : command.trim();
        if (trimmed.isEmpty()) {
            return parts;
        }

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;

        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (inQuotes) {
                if (ch == quoteChar) {
                    inQuotes = false;
                } else {
                    current.append(ch);
                }
            } else if (ch == '"' || ch == '\'') {
                inQuotes = true;
                quoteChar = ch;
            } else if (Character.isWhitespace(ch)) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(ch);
            }
        }

        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
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
        String scriptLocation = readConfigValue(PYTHON_SCRIPT_PATH_KEY, PYTHON_SCRIPT_PATH_ENV);
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

    private String readConfigValue(String sysParamKey, String envKey) {
        String value = readParamValue(sysParamKey);
        if (!value.isEmpty()) {
            return value;
        }
        String envValue = System.getenv(envKey);
        return envValue == null ? "" : envValue.trim();
    }

    private static final class TriggerWindow {
        private final Integer startDate;
        private final Integer endDate;

        private TriggerWindow(Integer startDate, Integer endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }

    private static final class PythonExecutionResult {
        private final boolean noWork;

        private PythonExecutionResult(boolean noWork) {
            this.noWork = noWork;
        }
    }

    private static final class CommandResult {
        private final int exitCode;
        private final String output;

        private CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
        }
    }
}

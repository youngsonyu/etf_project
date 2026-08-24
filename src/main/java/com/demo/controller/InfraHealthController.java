package com.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.entity.SysParam;
import com.demo.service.SysParamService;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/infra")
public class InfraHealthController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ConnectionFactory rabbitConnectionFactory;
    private final SysParamService sysParamService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${app.infra.redis.enabled:false}")
    private boolean redisEnabled;

    @Value("${app.infra.rabbitmq.enabled:false}")
    private boolean rabbitMqEnabled;

    @Value("${app.infra.nacos.enabled:false}")
    private boolean nacosEnabled;

    @Value("${spring.cloud.nacos.server-addr:127.0.0.1:8848}")
    private String nacosServerAddr;

    public InfraHealthController(JdbcTemplate jdbcTemplate,
                                 ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
                                 ObjectProvider<ConnectionFactory> rabbitConnectionFactoryProvider,
                                 SysParamService sysParamService) {
        this.jdbcTemplate = jdbcTemplate;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        this.rabbitConnectionFactory = rabbitConnectionFactoryProvider.getIfAvailable();
        this.sysParamService = sysParamService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mysql", checkMysql());
        result.put("redis", checkRedis());
        result.put("rabbitmq", checkRabbitMq());
        result.put("nacos", checkNacos());
        return result;
    }

    private Map<String, Object> checkMysql() {
        return check(() -> jdbcTemplate.queryForObject("SELECT 1", Integer.class));
    }

    private Map<String, Object> checkRedis() {
        if (!redisEnabled) {
            return skipped("disabled in local profile");
        }
        return check(() -> {
            String host = readParamValue("infra.redis.host", null);
            Integer port = readIntParam("infra.redis.port", null);
            String password = readParamValue("infra.redis.password", null);

            if (host == null || port == null) {
                if (stringRedisTemplate == null) {
                    return null;
                }
                String key = "infra:health:redis";
                stringRedisTemplate.opsForValue().set(key, "ok", Duration.ofSeconds(10));
                return stringRedisTemplate.opsForValue().get(key);
            }

            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
            if (password != null && !password.isBlank()) {
                config.setPassword(password);
            }
            LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
            factory.afterPropertiesSet();
            try (RedisConnection connection = factory.getConnection()) {
                return connection.ping();
            } finally {
                factory.destroy();
            }
        });
    }

    private Map<String, Object> checkRabbitMq() {
        if (!rabbitMqEnabled) {
            return skipped("disabled in local profile");
        }
        return check(() -> {
            String host = readParamValue("infra.rabbitmq.host", null);
            Integer port = readIntParam("infra.rabbitmq.port", null);
            String username = readParamValue("infra.rabbitmq.username", null);
            String password = readParamValue("infra.rabbitmq.password", null);
            String virtualHost = readParamValue("infra.rabbitmq.virtual-host", "/");

            if (host == null || port == null || username == null || password == null) {
                if (rabbitConnectionFactory == null) {
                    return null;
                }
                try (Connection connection = rabbitConnectionFactory.createConnection()) {
                    return connection.isOpen() ? "ok" : null;
                }
            }

            CachingConnectionFactory tempFactory = new CachingConnectionFactory(host, port);
            tempFactory.setUsername(username);
            tempFactory.setPassword(password);
            tempFactory.setVirtualHost(virtualHost == null || virtualHost.isBlank() ? "/" : virtualHost);
            try (Connection connection = tempFactory.createConnection()) {
                return connection.isOpen() ? "ok" : null;
            } finally {
                tempFactory.destroy();
            }
        });
    }

    private Map<String, Object> checkNacos() {
        if (!nacosEnabled) {
            return skipped("disabled in local profile");
        }
        return check(() -> {
            String dbAddr = readParamValue("infra.nacos.server-addr", null);
            String effectiveAddr = (dbAddr == null || dbAddr.isBlank()) ? nacosServerAddr : dbAddr;
            String url = "http://" + effectiveAddr + "/nacos/v1/console/health";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300 && response.body() != null) {
                return response.body().trim();
            }
            return null;
        });
    }

    private Map<String, Object> check(Probe probe) {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            Object value = probe.run();
            data.put("ok", true);
            data.put("detail", value == null ? "connected" : value);
        } catch (DataAccessException ex) {
            data.put("ok", false);
            data.put("detail", ex.getMostSpecificCause() == null ? ex.getMessage() : ex.getMostSpecificCause().getMessage());
        } catch (Exception ex) {
            data.put("ok", false);
            data.put("detail", ex.getMessage());
        }
        return data;
    }

    private Map<String, Object> skipped(String detail) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("detail", detail);
        return data;
    }

    private String readParamValue(String key, String defaultValue) {
        LambdaQueryWrapper<SysParam> wrapper = new LambdaQueryWrapper<SysParam>()
                .eq(SysParam::getParamKey, key)
                .eq(SysParam::getIsActive, 1)
                .orderByDesc(SysParam::getId)
                .last("LIMIT 1");
        SysParam param = sysParamService.getOne(wrapper, false);
        if (param == null || param.getParamValue() == null || param.getParamValue().trim().isEmpty()) {
            return defaultValue;
        }
        return param.getParamValue().trim();
    }

    private Integer readIntParam(String key, Integer defaultValue) {
        String value = readParamValue(key, null);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    @FunctionalInterface
    private interface Probe {
        Object run() throws Exception;
    }
}

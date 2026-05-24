package com.demo.utils;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

@Data
public class R {
    private int code;
    private String message;
    private Object data;

    public static R ok() {
        R r = new R();
        r.setCode(200);
        r.setMessage("success");
        r.setData(new HashMap<>());
        return r;
    }

    public static R ok(Object data) {
        R r = new R();
        r.setCode(200);
        r.setMessage("success");
        r.setData(data);
        return r;
    }

    public static R error(String message) {
        R r = new R();
        r.setCode(500);
        r.setMessage(message);
        r.setData(new HashMap<>());
        return r;
    }

    public R data(String key, Object value) {
        if (this.data == null || !(this.data instanceof Map)) {
            this.data = new HashMap<>();
        }
        ((Map<String, Object>) this.data).put(key, value);
        return this;
    }
}

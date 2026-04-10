package com.example.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * application.properties를 로드해 설정값을 제공한다.
 */
public class AppConfig {

    private static final Properties props = new Properties();

    static {
        try (InputStream in = AppConfig.class.getResourceAsStream("/application.properties")) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("application.properties 로드 실패", e);
        }
    }

    public static String get(String key) {
        return props.getProperty(key);
    }
}

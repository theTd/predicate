package com.mineclay.predicate;

import lombok.SneakyThrows;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;

public class BuiltinMethods extends PredicateMethodBase {
    @SneakyThrows
    @Override
    protected Object getProperty(String key) {
        switch (key) {
            case "hostname":
                return InetAddress.getLocalHost().getHostName();
            case "pid":
                return pid();
            default:
                return null;
        }
    }

    private String pid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        return name.substring(0, name.indexOf('@'));
    }
}

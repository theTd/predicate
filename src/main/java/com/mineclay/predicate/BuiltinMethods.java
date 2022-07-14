package com.mineclay.predicate;

import lombok.SneakyThrows;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;

public class BuiltinMethods extends PredicateMethodBase {
    @SneakyThrows
    public String hostname() {
        return InetAddress.getLocalHost().getHostName();
    }

    public String pid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        return name.substring(0, name.indexOf('@'));
    }
}

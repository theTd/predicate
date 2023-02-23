package com.mineclay.predicate;

import com.mineclay.server_info.ServerInfo;

public class CircleLinkMethods extends PredicateMethodBase {
    @Override
    protected Object getProperty(String key) {
        switch (key) {
            case "servername":
                return ServerInfo.getSelf().getName();
            case "tags":
                return ((ServerInfo) ServerInfo.getSelf()).getTags();
            case "serverinfo":
                return ServerInfo.getSelf();
            default:
                return null;
        }
    }
}

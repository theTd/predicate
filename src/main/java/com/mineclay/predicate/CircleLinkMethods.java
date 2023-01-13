package com.mineclay.predicate;

import com.mineclay.server_info.ServerInfo;

public class CircleLinkMethods extends PredicateMethodBase {
    @Override
    protected Object getProperty(String key) {
        switch (key) {
            //noinspection SpellCheckingInspection
            case "servername":
                return ServerInfo.getSelf().getName();
            case "tags":
                return ((ServerInfo) ServerInfo.getSelf()).getTags();
            //noinspection SpellCheckingInspection
            case "serverinfo":
                return ServerInfo.getSelf();
            default:
                return null;
        }
    }
}

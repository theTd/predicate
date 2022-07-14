package com.mineclay.predicate;

import com.mineclay.server_info.ServerInfo;

public class CircleLinkMethods extends PredicateMethodBase {

    public String servername() {
        return ServerInfo.getSelf().getName();
    }

    public ServerInfo server() {
        return (ServerInfo) ServerInfo.getSelf();
    }
}

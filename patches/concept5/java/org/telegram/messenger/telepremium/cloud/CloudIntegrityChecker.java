package org.telegram.messenger.telepremium.cloud;

import java.util.HashSet;
import java.util.Set;

public final class CloudIntegrityChecker {
    public static final class Result {
        public int nodes, errors, orphanNodes, duplicateIds, cycles;
        public boolean ok(){ return errors==0; }
    }
    public Result check(CloudSnapshot snapshot) {
        Result r=new Result(); if(snapshot==null){r.errors++;return r;}
        Set<String> ids=new HashSet<>(); ids.add("root");
        for(CloudNode n:snapshot.nodes){r.nodes++; if(n.id==null || !ids.add(n.id)){r.duplicateIds++;r.errors++;}}
        for(CloudNode n:snapshot.nodes){
            if(!"root".equals(n.id) && n.parentId!=null && !ids.contains(n.parentId)){r.orphanNodes++;r.errors++;}
            Set<String> seen=new HashSet<>(); CloudNode cur=n;
            while(cur!=null && cur.parentId!=null){
                if(!seen.add(cur.id)){r.cycles++;r.errors++;break;}
                cur=find(snapshot,cur.parentId);
            }
        }
        return r;
    }
    private CloudNode find(CloudSnapshot s,String id){for(CloudNode n:s.nodes)if(id.equals(n.id))return n;return null;}
}

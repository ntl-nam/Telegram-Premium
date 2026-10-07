package org.telegram.messenger.telepremium.cloud;

import java.util.List;

public final class CloudRecoveryEngine {
    public CloudSnapshot recover(List<CloudSnapshot> candidates) {
        CloudSnapshot best=null; CloudIntegrityChecker checker=new CloudIntegrityChecker();
        if(candidates==null)return null;
        for(CloudSnapshot s:candidates){
            if(s==null||!checker.check(s).ok())continue;
            if(best==null||s.generation>best.generation||(s.generation==best.generation&&s.updatedAt>best.updatedAt))best=s;
        }
        return best;
    }
}

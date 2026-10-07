package org.telegram.messenger.telepremium.cloud;

public final class CloudConflictResolver {
    public CloudSnapshot choose(CloudSnapshot local, CloudSnapshot remote) {
        if(local==null)return remote;if(remote==null)return local;
        if(!local.cloudId.equals(remote.cloudId))return remote;
        if(remote.generation>local.generation)return remote;
        if(local.generation>remote.generation)return local;
        return remote.updatedAt>=local.updatedAt?remote:local;
    }
    public boolean isConflict(CloudSnapshot local,CloudSnapshot remote){
        return local!=null&&remote!=null&&local.cloudId.equals(remote.cloudId)&&local.generation==remote.generation&&local.updatedAt!=remote.updatedAt;
    }
}

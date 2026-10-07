package org.telegram.messenger.telepremium.cloud;

public final class CloudTransfer {
    public static final int QUEUED=0, HASHING=1, UPLOADING=2, COMPLETE=3, FAILED=4;
    public final String nodeId;
    public int state=QUEUED;
    public float progress;
    public String error;
    public CloudTransfer(String nodeId){this.nodeId=nodeId;}
}

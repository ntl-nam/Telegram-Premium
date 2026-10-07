package org.telegram.messenger.telepremium.cloud;

import org.json.JSONObject;
import java.util.UUID;

public final class CloudJournalEntry {
    public static final String CREATE="CREATE", UPDATE="UPDATE", MOVE="MOVE", RENAME="RENAME", TRASH="TRASH", RESTORE="RESTORE", DELETE="DELETE", FAVORITE="FAVORITE";
    public String operationId=UUID.randomUUID().toString();
    public String nodeId, operation, clientId;
    public long generation, timestamp=System.currentTimeMillis();
    public JSONObject payload=new JSONObject();

    public JSONObject toJson() throws Exception {
        JSONObject o=new JSONObject();o.put("operationId",operationId);o.put("nodeId",nodeId);o.put("operation",operation);o.put("clientId",clientId);o.put("generation",generation);o.put("timestamp",timestamp);o.put("payload",payload);return o;
    }
    public static CloudJournalEntry fromJson(JSONObject o){
        CloudJournalEntry e=new CloudJournalEntry();e.operationId=o.optString("operationId");e.nodeId=o.optString("nodeId");e.operation=o.optString("operation");e.clientId=o.optString("clientId");e.generation=o.optLong("generation");e.timestamp=o.optLong("timestamp");e.payload=o.optJSONObject("payload");if(e.payload==null)e.payload=new JSONObject();return e;
    }
}

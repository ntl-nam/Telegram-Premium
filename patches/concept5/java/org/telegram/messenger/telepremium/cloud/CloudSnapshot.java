package org.telegram.messenger.telepremium.cloud;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CloudSnapshot {
    public static final int SCHEMA_VERSION = 1;
    public String cloudId = UUID.randomUUID().toString();
    public long ownerUserId;
    public long generation;
    public long updatedAt;
    public String clientId;
    public long journalGeneration;
    public final List<CloudNode> nodes = new ArrayList<>();

    public String encode() throws Exception {
        JSONObject root = new JSONObject();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("cloudId", cloudId);
        root.put("ownerUserId", ownerUserId);
        root.put("generation", generation);
        root.put("updatedAt", updatedAt);
        root.put("clientId", clientId);
        root.put("journalGeneration", journalGeneration);
        JSONArray a = new JSONArray();
        for (CloudNode n : nodes) a.put(n.toJson());
        root.put("nodes", a);
        return root.toString();
    }

    public static CloudSnapshot decode(String raw) throws Exception {
        JSONObject root = new JSONObject(raw);
        if (root.optInt("schemaVersion") != SCHEMA_VERSION) throw new IllegalStateException("Unsupported Cloud FS schema");
        CloudSnapshot s = new CloudSnapshot();
        s.cloudId=root.getString("cloudId"); s.ownerUserId=root.optLong("ownerUserId");
        s.generation=root.optLong("generation"); s.updatedAt=root.optLong("updatedAt");
        s.clientId=root.optString("clientId", null); s.journalGeneration=root.optLong("journalGeneration");
        JSONArray a=root.optJSONArray("nodes");
        if (a != null) for(int i=0;i<a.length();i++) s.nodes.add(CloudNode.fromJson(a.getJSONObject(i)));
        return s;
    }
}

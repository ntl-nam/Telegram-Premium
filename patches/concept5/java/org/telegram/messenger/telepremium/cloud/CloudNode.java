package org.telegram.messenger.telepremium.cloud;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public final class CloudNode {
    public static final int TYPE_FOLDER = 0;
    public static final int TYPE_FILE = 1;
    public static final int STATE_ACTIVE = 0;
    public static final int STATE_TRASHED = 1;

    public String id;
    public String parentId;
    public int type;
    public String name;
    public long createdAt;
    public long modifiedAt;
    public long deletedAt;
    public int state;
    public boolean favorite;
    public String mimeType;
    public long size;
    public long telegramMessageId;
    public long telegramDocumentId;
    public String sha256;
    public String originalName;
    public int offlineState;

    public static CloudNode file(String parentId, String name, String mimeType, long size, String sha256) {
        CloudNode n = new CloudNode();
        n.id = UUID.randomUUID().toString(); n.parentId = parentId; n.type = TYPE_FILE; n.name = name;
        n.originalName = name; n.mimeType = mimeType; n.size = size; n.sha256 = sha256;
        n.createdAt = n.modifiedAt = System.currentTimeMillis();
        return n;
    }

    public static CloudNode folder(String parentId, String name) {
        CloudNode n = new CloudNode();
        n.id = UUID.randomUUID().toString();
        n.parentId = parentId;
        n.type = TYPE_FOLDER;
        n.name = name;
        n.createdAt = n.modifiedAt = System.currentTimeMillis();
        return n;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id); o.put("parentId", parentId); o.put("type", type); o.put("name", name);
        o.put("createdAt", createdAt); o.put("modifiedAt", modifiedAt); o.put("deletedAt", deletedAt);
        o.put("state", state); o.put("favorite", favorite); o.put("mimeType", mimeType);
        o.put("size", size); o.put("telegramMessageId", telegramMessageId); o.put("telegramDocumentId", telegramDocumentId);
        o.put("sha256", sha256); o.put("originalName", originalName); o.put("offlineState", offlineState);
        return o;
    }

    public static CloudNode fromJson(JSONObject o) {
        CloudNode n = new CloudNode();
        n.id=o.optString("id"); n.parentId=o.optString("parentId", null); n.type=o.optInt("type");
        n.name=o.optString("name"); n.createdAt=o.optLong("createdAt"); n.modifiedAt=o.optLong("modifiedAt");
        n.deletedAt=o.optLong("deletedAt"); n.state=o.optInt("state"); n.favorite=o.optBoolean("favorite");
        n.mimeType=o.optString("mimeType", null); n.size=o.optLong("size");
        n.telegramMessageId=o.optLong("telegramMessageId"); n.telegramDocumentId=o.optLong("telegramDocumentId");
        n.sha256=o.optString("sha256", null); n.originalName=o.optString("originalName", null); n.offlineState=o.optInt("offlineState");
        return n;
    }
}

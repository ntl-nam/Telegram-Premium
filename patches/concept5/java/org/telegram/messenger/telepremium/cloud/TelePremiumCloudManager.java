package org.telegram.messenger.telepremium.cloud;

import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class TelePremiumCloudManager {
    private static final TelePremiumCloudManager[] INSTANCES = new TelePremiumCloudManager[UserConfig.MAX_ACCOUNT_COUNT];
    public static synchronized TelePremiumCloudManager getInstance(int account) {
        if (INSTANCES[account] == null) INSTANCES[account] = new TelePremiumCloudManager(account);
        return INSTANCES[account];
    }

    public interface Callback { void onComplete(String error); }
    private final int account;
    private final TelegramCloudMetadataStore store;
    private CloudSnapshot snapshot;
    private final String clientId=UUID.randomUUID().toString();
    private final CloudConflictResolver conflictResolver=new CloudConflictResolver();

    private TelePremiumCloudManager(int account) {
        this.account=account; this.store=new TelegramCloudMetadataStore(account);
    }

    public CloudSnapshot getSnapshot() { return snapshot; }

    public void bootstrap(Callback callback) {
        store.loadGenerations((generations, error) -> {
            if (error != null) { callback.onComplete(error); return; }
            CloudSnapshot loaded=new CloudRecoveryEngine().recover(generations);
            if (loaded != null) { snapshot=loaded; callback.onComplete(null); return; }
            snapshot=new CloudSnapshot();
            snapshot.clientId=clientId;
            TLRPC.User user=UserConfig.getInstance(account).getCurrentUser();
            snapshot.ownerUserId=user == null ? 0 : user.id;
            snapshot.generation=1;
            CloudNode root=CloudNode.folder(null, "Облако");
            root.id="root";
            snapshot.nodes.add(root);
            commit(callback);
        });
    }

    public List<CloudNode> children(String parentId, boolean trash) {
        if(snapshot==null) return Collections.emptyList();
        ArrayList<CloudNode> out=new ArrayList<>();
        for(CloudNode n:snapshot.nodes) {
            boolean parent = parentId == null ? n.parentId == null : parentId.equals(n.parentId);
            if(parent && !"root".equals(n.id) && (trash ? n.state==CloudNode.STATE_TRASHED : n.state==CloudNode.STATE_ACTIVE)) out.add(n);
        }
        Collections.sort(out, Comparator.comparingInt((CloudNode n)->n.type).thenComparing(n->n.name.toLowerCase()));
        return out;
    }

    public CloudNode findByHash(String sha256) {\n        if(snapshot==null || sha256==null) return null;\n        for(CloudNode n:snapshot.nodes) if(n.type==CloudNode.TYPE_FILE && sha256.equals(n.sha256) && n.state==CloudNode.STATE_ACTIVE) return n;\n        return null;\n    }\n\n    public void addFile(CloudNode node, Callback callback) {\n        if(snapshot==null) { callback.onComplete(\"Cloud is not ready\"); return; }\n        snapshot.nodes.add(node); commit(callback);\n    }\n\n    public void copy(CloudNode source, String parentId, Callback callback) {\n        CloudNode n = source.type==CloudNode.TYPE_FOLDER ? CloudNode.folder(parentId, source.name + \" (копия)\") : CloudNode.file(parentId, source.name, source.mimeType, source.size, source.sha256);\n        n.telegramMessageId=source.telegramMessageId; n.telegramDocumentId=source.telegramDocumentId; n.originalName=source.originalName;\n        snapshot.nodes.add(n); commit(callback);\n    }\n\n    public List<CloudNode> trashItems() {\n        if(snapshot==null) return Collections.emptyList();\n        ArrayList<CloudNode> out=new ArrayList<>(); for(CloudNode n:snapshot.nodes) if(n.state==CloudNode.STATE_TRASHED) out.add(n); return out;\n    }\n\n    public List<CloudNode> favorites() {\n        if(snapshot==null) return Collections.emptyList();\n        ArrayList<CloudNode> out=new ArrayList<>(); for(CloudNode n:snapshot.nodes) if(n.favorite && n.state==CloudNode.STATE_ACTIVE) out.add(n); return out;\n    }\n\n    public List<CloudNode> search(String query) {\n        if(snapshot==null || query==null) return Collections.emptyList();\n        String q=query.trim().toLowerCase(); ArrayList<CloudNode> out=new ArrayList<>();\n        for(CloudNode n:snapshot.nodes) if(n.state==CloudNode.STATE_ACTIVE && !\"root\".equals(n.id) && n.name!=null && n.name.toLowerCase().contains(q)) out.add(n);\n        return out;\n    }\n\n    public List<CloudNode> category(String kind) {\n        if(snapshot==null)return Collections.emptyList(); ArrayList<CloudNode> out=new ArrayList<>();\n        for(CloudNode n:snapshot.nodes){if(n.type!=CloudNode.TYPE_FILE||n.state!=CloudNode.STATE_ACTIVE)continue; String x=(n.name==null?\"\":n.name).toLowerCase(); String m=n.mimeType==null?\"\":n.mimeType;\n            boolean hit=(\"images\".equals(kind)&&m.startsWith(\"image/\")) || (\"video\".equals(kind)&&m.startsWith(\"video/\")) || (\"audio\".equals(kind)&&m.startsWith(\"audio/\")) || (\"apk\".equals(kind)&&x.endsWith(\".apk\")) || (\"archives\".equals(kind)&&(x.endsWith(\".zip\")||x.endsWith(\".rar\")||x.endsWith(\".7z\"))) || (\"documents\".equals(kind)&&(m.startsWith(\"text/\")||x.endsWith(\".pdf\")||x.endsWith(\".doc\")||x.endsWith(\".docx\")||x.endsWith(\".xls\")||x.endsWith(\".xlsx\")));\n            if(hit)out.add(n);}\n        return out;\n    }\n\n    public int payloadReferenceCount(CloudNode node) {\n        if(snapshot==null||node==null||node.telegramDocumentId==0)return 0; int count=0;\n        for(CloudNode n:snapshot.nodes)if(n.telegramDocumentId==node.telegramDocumentId && n.state!=CloudNode.STATE_TRASHED)count++; return count;\n    }\n\n    public void deleteMetadataPermanently(CloudNode node, Callback callback) {\n        if(snapshot==null||node==null){callback.onComplete(\"Cloud is not ready\");return;} snapshot.nodes.remove(node); commit(callback);\n    }\n\n    public CloudIntegrityChecker.Result checkIntegrity(){return new CloudIntegrityChecker().check(snapshot);}\n\n    public void createFolder(String parentId, String name, Callback callback) {
        if(snapshot==null) { callback.onComplete("Cloud is not ready"); return; }
        snapshot.nodes.add(CloudNode.folder(parentId, name));
        commit(callback);
    }

    public void rename(CloudNode node, String name, Callback callback) {
        node.name=name; node.modifiedAt=System.currentTimeMillis(); commit(callback);
    }

    public void move(CloudNode node, String parentId, Callback callback) {
        node.parentId=parentId; node.modifiedAt=System.currentTimeMillis(); commit(callback);
    }

    public void trash(CloudNode node, Callback callback) {
        node.state=CloudNode.STATE_TRASHED; node.deletedAt=System.currentTimeMillis(); commit(callback);
    }

    public void restore(CloudNode node, Callback callback) {
        node.state=CloudNode.STATE_ACTIVE; node.deletedAt=0; commit(callback);
    }

    public void toggleFavorite(CloudNode node, Callback callback) {
        node.favorite=!node.favorite; commit(callback);
    }

    private void commit(Callback callback) {
        final long expected=snapshot.generation;
        store.loadLatest((remote,error)->{
            if(error!=null){callback.onComplete(error);return;}
            if(remote!=null && remote.generation>expected){snapshot=conflictResolver.choose(snapshot,remote);callback.onComplete("Облако изменено на другом устройстве. Выполнена синхронизация; повторите действие.");return;}
            snapshot.generation=expected+1; snapshot.journalGeneration++; snapshot.clientId=clientId;
            store.save(snapshot, saveError -> callback.onComplete(saveError));
        });
    }
}

package org.telegram.messenger.telepremium.cloud;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;
import java.util.List;

public final class TelegramCloudMetadataStore {
    public static final String PREFIX = "#TELEPREMIUM_CLOUD_V1 ";
    private final int account;

    public interface LoadCallback { void onLoaded(CloudSnapshot snapshot, String error); }
    public interface SaveCallback { void onSaved(String error); }
    public interface HistoryCallback { void onLoaded(List<CloudSnapshot> snapshots, String error); }

    public TelegramCloudMetadataStore(int account) { this.account = account; }

    public void loadLatest(LoadCallback callback) {
        TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = new TLRPC.TL_inputPeerSelf();
        req.limit = 100;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            CloudSnapshot best = null;
            String failure = null;
            if (error != null) {
                failure = error.text;
            } else if (response instanceof TLRPC.messages_Messages) {
                for (TLRPC.Message m : ((TLRPC.messages_Messages) response).messages) {
                    if (m.message == null || !m.message.startsWith(PREFIX)) continue;
                    try {
                        CloudSnapshot candidate = CloudSnapshot.decode(m.message.substring(PREFIX.length()));
                        if (best == null || candidate.generation > best.generation) best = candidate;
                    } catch (Exception ignore) {}
                }
            }
            CloudSnapshot result = best;
            String finalFailure = failure;
            AndroidUtilities.runOnUIThread(() -> callback.onLoaded(result, finalFailure));
        });
    }

    public void loadGenerations(HistoryCallback callback) {
        TLRPC.TL_messages_getHistory req=new TLRPC.TL_messages_getHistory();req.peer=new TLRPC.TL_inputPeerSelf();req.limit=100;
        ConnectionsManager.getInstance(account).sendRequest(req,(response,error)->{
            ArrayList<CloudSnapshot> out=new ArrayList<>();
            if(error==null && response instanceof TLRPC.messages_Messages) for(TLRPC.Message m:((TLRPC.messages_Messages)response).messages) if(m.message!=null&&m.message.startsWith(PREFIX)) try{out.add(CloudSnapshot.decode(m.message.substring(PREFIX.length())));}catch(Exception ignore){}
            AndroidUtilities.runOnUIThread(()->callback.onLoaded(out,error==null?null:error.text));
        });
    }

    public void save(CloudSnapshot snapshot, SaveCallback callback) {
        try {
            snapshot.updatedAt = System.currentTimeMillis();
            TLRPC.TL_messages_sendMessage req = new TLRPC.TL_messages_sendMessage();
            req.peer = new TLRPC.TL_inputPeerSelf();
            req.message = PREFIX + snapshot.encode();
            req.random_id = org.telegram.messenger.Utilities.random.nextLong();
            req.no_webpage = true;
            ConnectionsManager.getInstance(account).sendRequest(req, (response, error) ->
                    AndroidUtilities.runOnUIThread(() -> callback.onSaved(error == null ? null : error.text)));
        } catch (Exception e) {
            callback.onSaved(e.getMessage());
        }
    }
}

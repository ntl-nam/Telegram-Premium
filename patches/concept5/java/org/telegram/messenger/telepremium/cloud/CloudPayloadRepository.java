package org.telegram.messenger.telepremium.cloud;

import android.net.Uri;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class CloudPayloadRepository implements NotificationCenter.NotificationCenterDelegate {
    private final int account;
    private Pending pending;

    public interface ImportCallback { void onComplete(CloudNode node, String error); }
    public interface DedupLookup { CloudNode find(String sha256); }
    private DedupLookup dedupLookup;
    public void setDedupLookup(DedupLookup lookup){this.dedupLookup=lookup;}
    private static final class Pending {
        CloudNode node; File temp; ImportCallback callback;
        Pending(CloudNode node, File temp, ImportCallback callback){this.node=node;this.temp=temp;this.callback=callback;}
    }

    public CloudPayloadRepository(int account){this.account=account;}

    public void importUri(Uri uri, String parentId, String displayName, String mime, ImportCallback callback) {
        if(pending!=null){callback.onComplete(null,"Уже выполняется загрузка");return;}
        Utilities.globalQueue.postRunnable(() -> {
            File temp=null;
            try {
                String safe=(displayName==null||displayName.trim().isEmpty())?"cloud-file":displayName.replaceAll("[\\\\/:*?\"<>|]","_");
                temp=new File(ApplicationLoader.applicationContext.getCacheDir(),"tpcloud_"+System.nanoTime()+"_"+safe);
                try(InputStream in=ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
                    FileOutputStream out=new FileOutputStream(temp)) {
                    if(in==null) throw new IllegalStateException("Не удалось открыть выбранный файл");
                    byte[] b=new byte[1024*1024]; int n; while((n=in.read(b))>0) out.write(b,0,n);
                }
                CloudNode node=CloudNode.file(parentId,safe,mime,temp.length(),CloudFileHasher.sha256(temp));
                CloudNode existing=dedupLookup==null?null:dedupLookup.find(node.sha256);
                if(existing!=null && existing.telegramDocumentId!=0){
                    node.telegramDocumentId=existing.telegramDocumentId; node.telegramMessageId=existing.telegramMessageId;
                    temp.delete(); AndroidUtilities.runOnUIThread(() -> callback.onComplete(node,null)); return;
                }
                File staged=temp;
                AndroidUtilities.runOnUIThread(() -> sendToSavedMessages(node,staged,callback));
            } catch(Exception e) {
                if(temp!=null) temp.delete(); FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> callback.onComplete(null,e.getMessage()));
            }
        });
    }

    private void sendToSavedMessages(CloudNode node, File file, ImportCallback callback) {
        pending=new Pending(node,file,callback);
        NotificationCenter.getInstance(account).addObserver(this,NotificationCenter.messageReceivedByServer);
        long selfId=UserConfig.getInstance(account).getClientUserId();
        SendMessagesHelper.prepareSendingDocument(
                account,
                file.getAbsolutePath(),
                file.getAbsolutePath(),
                null,
                node.name,
                node.mimeType,
                selfId,
                null,
                null,
                null,
                null,
                false,
                0
        );
    }

    @Override public void didReceivedNotification(int id, int accountId, Object... args) {
        if(id!=NotificationCenter.messageReceivedByServer || pending==null) return;
        try {
            Object obj=args.length>2?args[2]:null;
            if(!(obj instanceof TLRPC.Message)) return;
            TLRPC.Message message=(TLRPC.Message)obj;
            if(message.peer_id==null || message.peer_id.user_id!=UserConfig.getInstance(account).getClientUserId()) return;
            if(message.media==null || message.media.document==null) return;
            pending.node.telegramMessageId=message.id;
            pending.node.telegramDocumentId=message.media.document.id;
            Pending done=pending; pending=null;
            NotificationCenter.getInstance(account).removeObserver(this,NotificationCenter.messageReceivedByServer);
            if(done.temp!=null) done.temp.delete();
            done.callback.onComplete(done.node,null);
        } catch(Exception e) {
            FileLog.e(e);
        }
    }

    public File resolveDownloadedFile(TLRPC.Document document) {
        return document==null?null:FileLoader.getInstance(account).getPathToAttach(document,true);
    }

    public void cancel() {
        if(pending==null)return;
        Pending done=pending; pending=null;
        NotificationCenter.getInstance(account).removeObserver(this,NotificationCenter.messageReceivedByServer);
        if(done.temp!=null)done.temp.delete();
        done.callback.onComplete(null,"Отменено");
    }
}

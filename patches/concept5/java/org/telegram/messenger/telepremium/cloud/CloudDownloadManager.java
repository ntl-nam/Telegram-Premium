package org.telegram.messenger.telepremium.cloud;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.File;

public final class CloudDownloadManager implements NotificationCenter.NotificationCenterDelegate {
    public static final int CLOUD_ONLY=0, DOWNLOADING=1, OFFLINE_AVAILABLE=2, ERROR=3;
    private final int account;
    private CloudNode activeNode;
    private TLRPC.Document activeDocument;
    private TLRPC.Document lastResolvedDocument;
    private Callback activeCallback;

    public interface Callback { void onState(int state, File file, String error); }

    public CloudDownloadManager(int account){this.account=account;}

    public void download(CloudNode node, Callback callback) {
        if(node==null || node.telegramMessageId==0){callback.onState(ERROR,null,"У файла нет Telegram payload");return;}
        TLRPC.TL_messages_getMessages req=new TLRPC.TL_messages_getMessages();
        req.id.add((int)node.telegramMessageId);
        ConnectionsManager.getInstance(account).sendRequest(req,(response,error)->AndroidUtilities.runOnUIThread(()->{
            if(error!=null){callback.onState(ERROR,null,error.text);return;}
            if(!(response instanceof TLRPC.messages_Messages) || ((TLRPC.messages_Messages)response).messages.isEmpty()){
                callback.onState(ERROR,null,"Файл не найден в облаке");return;
            }
            TLRPC.Message m=((TLRPC.messages_Messages)response).messages.get(0);
            if(m.media==null || m.media.document==null){callback.onState(ERROR,null,"Telegram document отсутствует");return;}
            TLRPC.Document d=m.media.document;
            lastResolvedDocument=d;
            File existing=FileLoader.getInstance(account).getPathToAttach(d,true);
            if(existing.exists()){node.offlineState=OFFLINE_AVAILABLE;callback.onState(OFFLINE_AVAILABLE,existing,null);return;}
            activeNode=node;activeDocument=d;activeCallback=callback;node.offlineState=DOWNLOADING;
            NotificationCenter.getInstance(account).addObserver(this,NotificationCenter.fileLoaded);
            NotificationCenter.getInstance(account).addObserver(this,NotificationCenter.fileLoadFailed);
            FileLoader.getInstance(account).loadFile(d,m,FileLoader.PRIORITY_NORMAL,0);
            callback.onState(DOWNLOADING,null,null);
        }));
    }

    public boolean releaseOffline(CloudNode node) {
        if(node==null || node.telegramMessageId==0)return false;
        TLRPC.Document d=(activeNode==node?activeDocument:lastResolvedDocument);
        if(d==null || d.id!=node.telegramDocumentId)return false;
        FileLoader.getInstance(account).cancelLoadFile(d);
        File file=FileLoader.getInstance(account).getPathToAttach(d,true);
        boolean ok=!file.exists() || file.delete();
        if(ok) node.offlineState=CLOUD_ONLY;
        return ok;
    }

    @Override public void didReceivedNotification(int id,int accountId,Object... args) {
        if(activeDocument==null || activeCallback==null)return;
        String expected=FileLoader.getAttachFileName(activeDocument);
        String actual=args.length>0 && args[0] instanceof String?(String)args[0]:null;
        if(actual!=null && !expected.equals(actual))return;
        if(id==NotificationCenter.fileLoaded){
            File f=FileLoader.getInstance(account).getPathToAttach(activeDocument,true);
            activeNode.offlineState=OFFLINE_AVAILABLE; finish(OFFLINE_AVAILABLE,f,null);
        } else if(id==NotificationCenter.fileLoadFailed){
            activeNode.offlineState=ERROR; finish(ERROR,null,"Не удалось скачать файл");
        }
    }

    private void finish(int state,File file,String error){
        Callback cb=activeCallback;activeCallback=null;activeNode=null;activeDocument=null;
        NotificationCenter.getInstance(account).removeObserver(this,NotificationCenter.fileLoaded);
        NotificationCenter.getInstance(account).removeObserver(this,NotificationCenter.fileLoadFailed);
        cb.onState(state,file,error);
    }
}

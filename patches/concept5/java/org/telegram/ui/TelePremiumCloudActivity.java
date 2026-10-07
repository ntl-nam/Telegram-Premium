package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.DialogInterface;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.telepremium.cloud.CloudNode;
import org.telegram.messenger.telepremium.cloud.TelePremiumCloudManager;
import org.telegram.messenger.telepremium.cloud.CloudPayloadRepository;
import org.telegram.messenger.telepremium.cloud.CloudDownloadManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;

import java.util.List;

public class TelePremiumCloudActivity extends BaseFragment {
    private final int account;
    private final String folderId;
    private final String folderName;
    private LinearLayout content;
    private TelePremiumCloudManager cloud;
    private CloudPayloadRepository payloads;
    private CloudDownloadManager downloads;
    private static final int PICK_FILE=7301;

    public TelePremiumCloudActivity(int account) { this(account, "root", "Облако"); }
    private TelePremiumCloudActivity(int account, String folderId, String folderName) {
        this.account=account; this.folderId=folderId; this.folderName=folderName;
    }

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(folderName);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();}});
        ScrollView scroll=new ScrollView(context);
        content=new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        fragmentView=scroll;
        cloud=TelePremiumCloudManager.getInstance(account);
        payloads=new CloudPayloadRepository(account);
        payloads.setDedupLookup(sha256 -> cloud.findByHash(sha256));
        downloads=new CloudDownloadManager(account);
        showStatus(context, "Синхронизация облака…");
        cloud.bootstrap(error -> {
            if(error!=null) showStatus(context, "Не удалось синхронизировать: "+error);
            else render(context);
        });
        return fragmentView;
    }

    private void render(Context context) {
        content.removeAllViews();
        TextCell upload=new TextCell(context, getResourceProvider());
        upload.setTextAndValue("＋ Загрузить файл", "Выбрать файл на устройстве", true);
        upload.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        upload.setOnClickListener(v -> { Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); startActivityForResult(i,PICK_FILE); });
        content.addView(upload,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(58)));

        TextCell create=new TextCell(context, getResourceProvider());
        create.setTextAndValue("＋ Новая папка", "Создать каталог в TelePremium Cloud", true);
        create.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        create.setOnClickListener(v -> createFolder(context));
        content.addView(create, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(58)));

        List<CloudNode> nodes=cloud.children(folderId, false);
        if(nodes.isEmpty()) {
            showStatus(context, "Папка пуста\nСтруктура хранится в Telegram Cloud и восстановится после переустановки.");
            return;
        }
        for(CloudNode node:nodes) {
            TextCell cell=new TextCell(context, getResourceProvider());
            String state=node.offlineState==CloudDownloadManager.OFFLINE_AVAILABLE?"✓ Offline":node.offlineState==CloudDownloadManager.DOWNLOADING?"Скачивание":"☁";
            String value=node.type==CloudNode.TYPE_FOLDER ? "Папка · ☁" : humanSize(node.size)+" · "+state;
            cell.setTextAndValue((node.type==CloudNode.TYPE_FOLDER ? "📁 " : "📄 ")+node.name, value, true);
            cell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            cell.setOnClickListener(v -> {
                if(node.type==CloudNode.TYPE_FOLDER) presentFragment(new TelePremiumCloudActivity(account,node.id,node.name));
                else downloads.download(node,(state,file,error)-> {
                    if(error!=null) showStatus(context,"Ошибка: "+error);
                    else if(state==CloudDownloadManager.DOWNLOADING) showStatus(context,"Скачивание "+node.name+"…");
                    else render(context);
                });
            });
            cell.setOnLongClickListener(v -> { showNodeMenu(context,node); return true; });
            content.addView(cell,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(58)));
        }
    }

    @Override public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        super.onActivityResultFragment(requestCode,resultCode,data);
        if(requestCode!=PICK_FILE || resultCode!=Activity.RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData(); String mime=getParentActivity().getContentResolver().getType(uri);
        showStatus(getParentActivity(),"Подготовка и загрузка файла…");
        payloads.importUri(uri,folderId,uri.getLastPathSegment(),mime,(node,error)-> {
            if(error!=null){showStatus(getParentActivity(),"Ошибка загрузки: "+error);return;}
            cloud.addFile(node,commitError->render(getParentActivity()));
        });
    }

    private void createFolder(Context context) {
        EditText input=new EditText(context); input.setHint("Имя папки"); input.setSingleLine(true);
        new AlertDialog.Builder(context).setTitle("Новая папка").setView(input)
                .setPositiveButton("Создать",(d,w)-> {
                    String name=input.getText().toString().trim();
                    if(!name.isEmpty()) cloud.createFolder(folderId,name,error->render(context));
                }).setNegativeButton("Отмена",null).show();
    }

    private void showNodeMenu(Context context, CloudNode node) {
        CharSequence[] items=node.type==CloudNode.TYPE_FILE ? new CharSequence[]{"Переименовать",node.favorite?"Убрать из избранного":"В избранное","Скачать / хранить офлайн","Освободить место","Переместить","Копировать","В корзину"} : new CharSequence[]{"Переименовать",node.favorite?"Убрать из избранного":"В избранное","Переместить","Копировать","В корзину"};
        new AlertDialog.Builder(context).setItems(items,(d,which)-> {
            if(which==0) rename(context,node);
            else if(which==1) cloud.toggleFavorite(node,error->render(context));
            else if(node.type==CloudNode.TYPE_FILE && which==2) downloads.download(node,(state,file,error)->render(context));
            else if(node.type==CloudNode.TYPE_FILE && which==3) { downloads.releaseOffline(node); cloud.rename(node,node.name,error->render(context)); }
            else if((node.type==CloudNode.TYPE_FILE && which==4)||(node.type==CloudNode.TYPE_FOLDER && which==2)) pickDestination(node,false,context);
            else if((node.type==CloudNode.TYPE_FILE && which==5)||(node.type==CloudNode.TYPE_FOLDER && which==3)) pickDestination(node,true,context);
            else cloud.trash(node,error->render(context));
        }).show();
    }

    private void pickDestination(CloudNode node, boolean copy, Context context) {
        presentFragment(new TelePremiumCloudFolderPickerActivity(account,"root",copy?"Копировать в":"Переместить в",target->{
            if(copy) cloud.copy(node,target,error->render(context)); else cloud.move(node,target,error->render(context));
        }));
    }

    private void rename(Context context, CloudNode node) {
        EditText input=new EditText(context); input.setText(node.name); input.setSingleLine(true);
        new AlertDialog.Builder(context).setTitle("Переименовать").setView(input)
                .setPositiveButton("Сохранить",(d,w)-> {
                    String name=input.getText().toString().trim();
                    if(!name.isEmpty()) cloud.rename(node,name,error->render(context));
                }).setNegativeButton("Отмена",null).show();
    }

    private void showStatus(Context context,String text) {
        content.removeAllViews();
        TextView v=new TextView(context); v.setText(text); v.setTextSize(15); v.setPadding(AndroidUtilities.dp(21),AndroidUtilities.dp(28),AndroidUtilities.dp(21),AndroidUtilities.dp(28));
        v.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText)); content.addView(v);
    }

    private String humanSize(long n) {
        if(n<1024)return n+" B"; if(n<1024*1024)return (n/1024)+" KB"; if(n<1024L*1024*1024)return (n/(1024*1024))+" MB"; return String.format("%.1f GB",n/(1024d*1024d*1024d));
    }
}

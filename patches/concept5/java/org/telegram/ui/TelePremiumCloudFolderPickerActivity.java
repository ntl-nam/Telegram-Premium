package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.telepremium.cloud.CloudNode;
import org.telegram.messenger.telepremium.cloud.TelePremiumCloudManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import java.util.List;

public class TelePremiumCloudFolderPickerActivity extends BaseFragment {
    public interface Callback { void onFolder(String id); }
    private final int account; private final String folderId; private final String title; private final Callback callback;
    public TelePremiumCloudFolderPickerActivity(int account,String folderId,String title,Callback callback){this.account=account;this.folderId=folderId;this.title=title;this.callback=callback;}
    @Override public View createView(Context context){
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);actionBar.setTitle(title);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();}});
        ScrollView s=new ScrollView(context);LinearLayout list=new LinearLayout(context);list.setOrientation(LinearLayout.VERTICAL);s.addView(list);fragmentView=s;
        TelePremiumCloudManager cloud=TelePremiumCloudManager.getInstance(account);
        TextCell here=new TextCell(context,getResourceProvider());here.setTextAndValue("Выбрать эту папку","Переместить/копировать сюда",true);here.setOnClickListener(v->{callback.onFolder(folderId);finishFragment();});list.addView(here,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(58)));
        for(CloudNode n:cloud.children(folderId,false))if(n.type==CloudNode.TYPE_FOLDER){TextCell c=new TextCell(context,getResourceProvider());c.setTextAndValue("📁 "+n.name,"Открыть",true);c.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));c.setOnClickListener(v->presentFragment(new TelePremiumCloudFolderPickerActivity(account,n.id,n.name,callback)));list.addView(c,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(58)));}
        return fragmentView;
    }
}

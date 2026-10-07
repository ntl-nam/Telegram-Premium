package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.telepremium.cloud.CloudNode;
import org.telegram.messenger.telepremium.cloud.TelePremiumCloudManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import java.util.List;

public class TelePremiumCloudSpecialActivity extends BaseFragment {
    public static final int FAVORITES=1, TRASH=2;
    private final int account, mode;
    private LinearLayout content;
    public TelePremiumCloudSpecialActivity(int account,int mode){this.account=account;this.mode=mode;}
    @Override public View createView(Context context){
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(mode==TRASH?"Корзина":"Избранное");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();}});
        ScrollView s=new ScrollView(context);content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);s.addView(content);fragmentView=s;
        TelePremiumCloudManager cloud=TelePremiumCloudManager.getInstance(account);
        cloud.bootstrap(error->render(context,cloud)); return fragmentView;
    }
    private void render(Context context,TelePremiumCloudManager cloud){
        content.removeAllViews(); List<CloudNode> nodes=mode==TRASH?cloud.trashItems():cloud.favorites();
        if(nodes.isEmpty()){TextView e=new TextView(context);e.setText(mode==TRASH?"Корзина пуста":"Нет избранных файлов");e.setPadding(AndroidUtilities.dp(21),AndroidUtilities.dp(28),0,0);content.addView(e);return;}
        for(CloudNode n:nodes){TextCell c=new TextCell(context,getResourceProvider());c.setTextAndValue((n.type==CloudNode.TYPE_FOLDER?"📁 ":"📄 ")+n.name,mode==TRASH?"Нажмите, чтобы восстановить":"Избранное",true);c.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            c.setOnClickListener(v->{if(mode==TRASH)cloud.restore(n,error->render(context,cloud));});content.addView(c,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(58)));}
    }
}

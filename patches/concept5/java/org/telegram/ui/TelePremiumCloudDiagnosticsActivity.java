package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.telepremium.cloud.CloudIntegrityChecker;
import org.telegram.messenger.telepremium.cloud.CloudSnapshot;
import org.telegram.messenger.telepremium.cloud.TelePremiumCloudManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.TextCell;

public class TelePremiumCloudDiagnosticsActivity extends BaseFragment {
    private final int account;
    public TelePremiumCloudDiagnosticsActivity(int account){this.account=account;}
    @Override public View createView(Context context){
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);actionBar.setTitle("Диагностика облака");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();}});
        ScrollView s=new ScrollView(context);LinearLayout list=new LinearLayout(context);list.setOrientation(LinearLayout.VERTICAL);s.addView(list);fragmentView=s;
        TelePremiumCloudManager cloud=TelePremiumCloudManager.getInstance(account);
        cloud.bootstrap(error->{
            list.removeAllViews();CloudSnapshot snap=cloud.getSnapshot();CloudIntegrityChecker.Result r=cloud.checkIntegrity();
            add(list,context,"Состояние",error==null?(r.ok()?"Готово":"Требуется восстановление"):error);
            add(list,context,"Cloud ID",snap==null?"—":snap.cloudId);
            add(list,context,"Поколение",snap==null?"—":String.valueOf(snap.generation));
            add(list,context,"Объекты",String.valueOf(r.nodes));
            add(list,context,"Ошибки целостности",String.valueOf(r.errors));
            add(list,context,"Потерянные связи",String.valueOf(r.orphanNodes));
            add(list,context,"Циклы каталогов",String.valueOf(r.cycles));
        });return fragmentView;
    }
    private void add(LinearLayout list,Context context,String name,String value){TextCell c=new TextCell(context,getResourceProvider());c.setTextAndValue(name,value,true);list.addView(c,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(58)));}
}

package org.telegram.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

public class TelePremiumQrLoginDialog extends Dialog {
    private final int account;
    private final Utilities.Callback2<TLRPC.TL_auth_authorization,String> done;
    private ImageView qr; private TextView status; private boolean stopped; private Runnable refresh;

    public TelePremiumQrLoginDialog(Context context,int account,Utilities.Callback2<TLRPC.TL_auth_authorization,String> done){
        super(context,android.R.style.Theme_Material_Light_NoActionBar); this.account=account; this.done=done;
    }
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout root=new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER_HORIZONTAL); root.setPadding(dp(20),dp(24),dp(20),dp(24)); root.setBackgroundColor(Color.WHITE);
        ImageButton back=new ImageButton(getContext()); back.setImageResource(android.R.drawable.ic_media_previous); back.setBackgroundColor(Color.TRANSPARENT); back.setOnClickListener(v->dismiss());
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(48),dp(48)); bp.gravity=Gravity.START; root.addView(back,bp);
        TextView title=new TextView(getContext()); title.setText("Вход по QR-коду"); title.setTextSize(24); title.setTextColor(Color.BLACK); title.setGravity(Gravity.CENTER); root.addView(title,new LinearLayout.LayoutParams(-1,-2));
        TextView help=new TextView(getContext()); help.setText("Откройте Telegram на уже авторизованном устройстве → Настройки → Устройства → Подключить устройство и отсканируйте этот код."); help.setTextSize(16); help.setTextColor(0xff555555); help.setGravity(Gravity.CENTER); LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.setMargins(dp(24),dp(16),dp(24),dp(20));root.addView(help,hp);
        qr=new ImageView(getContext()); root.addView(qr,new LinearLayout.LayoutParams(dp(280),dp(280)));
        status=new TextView(getContext()); status.setText("Получаем код…"); status.setTextSize(15); status.setTextColor(0xff777777); status.setGravity(Gravity.CENTER); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.setMargins(0,dp(18),0,0);root.addView(status,sp);
        setContentView(root,new ViewGroup.LayoutParams(-1,-1)); requestToken();
    }
    private int dp(int v){return AndroidUtilities.dp(v);}
    private void requestToken(){
        if(stopped)return; TLRPC.TL_auth_exportLoginToken req=new TLRPC.TL_auth_exportLoginToken(); req.api_id=BuildVars.APP_ID; req.api_hash=BuildVars.APP_HASH;
        for(int i=0;i<UserConfig.MAX_ACCOUNT_COUNT;i++){UserConfig u=UserConfig.getInstance(i);if(u.isClientActivated())req.except_ids.add(u.getClientUserId());}
        ConnectionsManager.getInstance(account).sendRequest(req,(res,err)->AndroidUtilities.runOnUIThread(()->handle(res,err==null?null:err.text)),ConnectionsManager.RequestFlagWithoutLogin);
    }
    private void handle(TLObject res,String err){
        if(stopped)return;
        if(err!=null){status.setText("Ошибка: "+err); schedule(3000);return;}
        if(res instanceof TLRPC.TL_auth_loginToken){TLRPC.TL_auth_loginToken t=(TLRPC.TL_auth_loginToken)res; String data="tg://login?token="+Base64.encodeToString(t.token,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING); render(data); status.setText("Ожидание сканирования…"); long ms=Math.max(3000L,Math.min(20000L,(t.expires-System.currentTimeMillis()/1000L-2L)*1000L));schedule(ms);}
        else if(res instanceof TLRPC.TL_auth_loginTokenMigrateTo){TLRPC.TL_auth_loginTokenMigrateTo m=(TLRPC.TL_auth_loginTokenMigrateTo)res;TLRPC.TL_auth_importLoginToken q=new TLRPC.TL_auth_importLoginToken();q.token=m.token;ConnectionsManager.getInstance(account).sendRequest(q,(r,e)->AndroidUtilities.runOnUIThread(()->handle(r,e==null?null:e.text)),null,null,ConnectionsManager.RequestFlagWithoutLogin,m.dc_id,ConnectionsManager.ConnectionTypeGeneric,true);}
        else if(res instanceof TLRPC.TL_auth_loginTokenSuccess){TLRPC.auth_Authorization a=((TLRPC.TL_auth_loginTokenSuccess)res).authorization;if(a instanceof TLRPC.TL_auth_authorization){dismiss();done.run((TLRPC.TL_auth_authorization)a,null);}else done.run(null,"Unsupported authorization response");}
    }
    private void schedule(long delay){if(refresh!=null)AndroidUtilities.cancelRunOnUIThread(refresh);refresh=this::requestToken;AndroidUtilities.runOnUIThread(refresh,delay);}
    private void render(String text){try{Bitmap b=new QRCodeWriter().encode(text,700,700,null,null);qr.setImageBitmap(b);}catch(Exception e){FileLog.e(e);status.setText("Не удалось создать QR-код");}}
    @Override public void dismiss(){stopped=true;if(refresh!=null)AndroidUtilities.cancelRunOnUIThread(refresh);super.dismiss();}
}
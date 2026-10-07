package org.telegram.messenger.telepremium;

import android.content.Context;
import android.os.CancellationSignal;
import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.GetPublicKeyCredentialOption;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.GetCredentialInterruptedException;
import androidx.credentials.exceptions.NoCredentialException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

public final class TelePremiumPasskeyController {
    private TelePremiumPasskeyController() {}
    public static Runnable login(Context context,int account,Utilities.Callback3<Long,TLRPC.auth_Authorization,String> done){
        final CredentialManager cm=CredentialManager.create(context);
        final boolean[] cancelled={false}; final Runnable[] cancel={null};
        TelePremiumPasskeyTL.InitLogin req=new TelePremiumPasskeyTL.InitLogin(); req.api_id=BuildVars.APP_ID; req.api_hash=BuildVars.APP_HASH;
        int id=ConnectionsManager.getInstance(account).sendRequest(req,(res,err)->AndroidUtilities.runOnUIThread(()->{
            if(cancelled[0])return;
            if(err!=null){done.run(0L,null,err.text);return;}
            try{
                TelePremiumPasskeyTL.LoginOptions opts=(TelePremiumPasskeyTL.LoginOptions)res;
                JSONObject root=new JSONObject(opts.options.data);
                GetCredentialRequest request=new GetCredentialRequest.Builder().addCredentialOption(new GetPublicKeyCredentialOption(root.getJSONObject("publicKey").toString())).build();
                CancellationSignal signal=new CancellationSignal(); cancel[0]=signal::cancel;
                cm.getCredentialAsync(context,request,signal,context.getMainExecutor(),new CredentialManagerCallback<GetCredentialResponse,GetCredentialException>(){
                    public void onResult(GetCredentialResponse rr){
                        try{
                            Credential cred=rr.getCredential(); JSONObject json=new JSONObject(cred.getData().getString("androidx.credentials.BUNDLE_KEY_AUTHENTICATION_RESPONSE_JSON"));
                            JSONObject response=json.getJSONObject("response");
                            TelePremiumPasskeyTL.FinishLogin f=new TelePremiumPasskeyTL.FinishLogin(); f.credential=new TelePremiumPasskeyTL.Credential();
                            f.credential.id=json.getString("id"); f.credential.raw_id=json.getString("rawId");
                            TelePremiumPasskeyTL.InputResponseLogin pr=new TelePremiumPasskeyTL.InputResponseLogin();
                            pr.client_data=new TLRPC.TL_dataJSON(); pr.client_data.data=new String(Base64.decode(response.getString("clientDataJSON"),Base64.URL_SAFE));
                            pr.authenticator_data=Base64.decode(response.getString("authenticatorData"),Base64.URL_SAFE);
                            pr.signature=Base64.decode(response.getString("signature"),Base64.URL_SAFE);
                            pr.user_handle=new String(Base64.decode(response.getString("userHandle"),Base64.URL_SAFE)); f.credential.response=pr;
                            String[] uh=pr.user_handle.split(":"); int dc=Integer.parseInt(uh[0]); long uid=Long.parseLong(uh[1]);
                            ConnectionsManager.getInstance(account).sendRequest(f,(a,e)->AndroidUtilities.runOnUIThread(()->done.run(uid,e==null?(TLRPC.auth_Authorization)a:null,e==null?null:e.text)),null,null,ConnectionsManager.RequestFlagWithoutLogin|ConnectionsManager.RequestFlagInvokeAfter,dc,ConnectionsManager.ConnectionTypeGeneric,true);
                        }catch(Exception e){FileLog.e(e);done.run(0L,null,e.getMessage());}
                    }
                    public void onError(@NonNull GetCredentialException e){if(e instanceof NoCredentialException)done.run(0L,null,"EMPTY");else if(e instanceof GetCredentialCancellationException||e instanceof GetCredentialInterruptedException)done.run(0L,null,"CANCELLED");else done.run(0L,null,e.getMessage());}
                });
            }catch(Exception e){FileLog.e(e);done.run(0L,null,e.getMessage());}
        }),ConnectionsManager.RequestFlagWithoutLogin);
        cancel[0]=()->ConnectionsManager.getInstance(account).cancelRequest(id,true);
        return ()->{cancelled[0]=true;if(cancel[0]!=null)cancel[0].run();};
    }
}
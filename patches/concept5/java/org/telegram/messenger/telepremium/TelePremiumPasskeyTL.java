package org.telegram.messenger.telepremium;

import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

public final class TelePremiumPasskeyTL {
    private TelePremiumPasskeyTL() {}

    public static class LoginOptions extends TLObject {
        public static final int constructor = 0xe2037789;
        public TLRPC.TL_dataJSON options;
        public static LoginOptions TLdeserialize(AbstractSerializedData s, int c, boolean e) {
            if (c != constructor) { if (e) throw new RuntimeException("can't parse passkeyLoginOptions"); return null; }
            LoginOptions r = new LoginOptions(); r.readParams(s,e); return r;
        }
        public void readParams(AbstractSerializedData s, boolean e) { options = TLRPC.TL_dataJSON.TLdeserialize(s,s.readInt32(e),e); }
        public void serializeToStream(AbstractSerializedData s) { s.writeInt32(constructor); options.serializeToStream(s); }
    }
    public static class InitLogin extends TLObject {
        public static final int constructor = 0x518ad0b7;
        public int api_id; public String api_hash;
        public TLObject deserializeResponse(AbstractSerializedData s,int c,boolean e){ return LoginOptions.TLdeserialize(s,c,e); }
        public void serializeToStream(AbstractSerializedData s){s.writeInt32(constructor);s.writeInt32(api_id);s.writeString(api_hash);}
    }
    public static class InputResponseLogin extends TLObject {
        public static final int constructor=0xc31fc14a;
        public TLRPC.TL_dataJSON client_data; public byte[] authenticator_data; public byte[] signature; public String user_handle;
        public void readParams(AbstractSerializedData s,boolean e){client_data=TLRPC.TL_dataJSON.TLdeserialize(s,s.readInt32(e),e);authenticator_data=s.readByteArray(e);signature=s.readByteArray(e);user_handle=s.readString(e);}
        public void serializeToStream(AbstractSerializedData s){s.writeInt32(constructor);client_data.serializeToStream(s);s.writeByteArray(authenticator_data);s.writeByteArray(signature);s.writeString(user_handle);}
    }
    public static class Credential extends TLObject {
        public static final int constructor=0x3c27b78f;
        public String id,raw_id; public InputResponseLogin response;
        public void serializeToStream(AbstractSerializedData s){s.writeInt32(constructor);s.writeString(id);s.writeString(raw_id);response.serializeToStream(s);}
    }
    public static class FinishLogin extends TLObject {
        public static final int constructor=0x9857ad07;
        public int flags,from_dc_id; public long from_auth_key_id; public Credential credential;
        public TLObject deserializeResponse(AbstractSerializedData s,int c,boolean e){return TLRPC.auth_Authorization.TLdeserialize(s,c,e);}
        public void serializeToStream(AbstractSerializedData s){s.writeInt32(constructor);s.writeInt32(flags);credential.serializeToStream(s);if((flags&1)!=0){s.writeInt32(from_dc_id);s.writeInt64(from_auth_key_id);}}
    }
}
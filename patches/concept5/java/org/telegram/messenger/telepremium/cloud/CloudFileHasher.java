package org.telegram.messenger.telepremium.cloud;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

public final class CloudFileHasher {
    private CloudFileHasher() {}
    public static String sha256(File file) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        byte[] buffer=new byte[1024*1024];
        try(FileInputStream in=new FileInputStream(file)) {
            int n; while((n=in.read(buffer))>0) md.update(buffer,0,n);
        }
        StringBuilder out=new StringBuilder();
        for(byte b:md.digest()) out.append(String.format("%02x",b));
        return out.toString();
    }
}

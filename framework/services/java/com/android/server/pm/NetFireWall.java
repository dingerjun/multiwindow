package com.android.server.pm;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.util.List;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import android.util.Log;

public class NetFireWall {
    
    static private final String NET_FIREWALL_CMD = "firewall";
    
    public static boolean setFireWall(List<FireWallItem> items) {
        if( items != null ) {
            int nsize = items.size();

            StringBuilder b = new StringBuilder();
            for( int i = 0; i < nsize; i++ ) {
                FireWallItem item = items.get(i);
                if( item != null && item.uid > 0 ) {
                   b.append(item.uid);
                   b.append(",");
                   if( item.internet_enabled ) {
                       b.append(1);
                   } else {
                       b.append(0);
                   }
                   b.append(",");
                   if( item.internet_wifi_enabled ) {
                       b.append(1); 
                   } else {
                       b.append(0);
                   }
                   b.append(",");
                   if( item.internet_gprs_enabled ) {
                       b.append(1);  
                   } else {
                       b.append(0);
                   }
                   b.append("\n");
                }
            }
            return updateFireWall(b.toString());
        }
        return false;
    }

    private static boolean updateFireWall(String str) {
        boolean success = false;
        if(str == null )
            return success;

        try {
            LocalSocketAddress address = new LocalSocketAddress("font_root");
            LocalSocket localSocket = new LocalSocket();
            localSocket.connect(address);
            if( !localSocket.isConnected() ) {
                return false;
            }

            PrintWriter socketWriter = new PrintWriter(localSocket.getOutputStream(), true);
            BufferedReader socketReader = new BufferedReader(new InputStreamReader(localSocket.getInputStream()));
            StringBuilder cmd = new StringBuilder();
            cmd.append(NET_FIREWALL_CMD);
            cmd.append(" ");
            cmd.append(str);

            socketWriter.write(cmd.toString());
            socketWriter.flush();
            String a = socketReader.readLine();
            if (a.startsWith("success")) {
                success = true;
            } else
                success = false;
                socketWriter.close();
                socketReader.close();
                localSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
                success = false;
            }
        return success;
    }
}

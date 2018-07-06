package com.example.a2017101705.rawmealogger;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.sql.Ref;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Created by 2017101705 on 2018/5/28.
 */

public class ntripClient implements Runnable {

    private String TAG = "ntripClient";
    private String casterIP = "202.136.213.10";
    private int casterPort = 9902;
    private String username = "nls";
    private String password = "gnss2016";
    private String gpgga;
    private double Lat;
    private double Lon;
    private int flg;
    private Socket socket = null;
    private InputStream is;
    private OutputStream os;
    private DataInputStream dis;
    private DataOutputStream dos;
    private int gx = 25578747;
    private double lam=299792458.0/1575420000.0;

//    public ntripClient(String ip, String port, String user, String pwd, String lat, String lon, int flag) {
        public ntripClient( int flag) {
//        if (ip != null) this.casterIP = ip;
//        if (port != null) this.casterPort = Integer.parseInt(port);
//        if(user!=null) this.username = user;
//        if(pwd!=null) this.password = pwd;
//        if(lat!=null) this.Lat = Double.parseDouble(lat);
//        if(lon!=null) this.Lon = Double.parseDouble(lon);
        this.flg = flag;
    }
    @Override
    public void run() {
        RefInfo[]  refInfo  = new RefInfo[32];
        for(int i=0;i<32;i++){
            refInfo[i] =new RefInfo();
        }
        try {
            SocketAddress sockaddr = new InetSocketAddress(casterIP, casterPort);
            socket = new Socket();
            socket.connect(sockaddr, 10 * 1000); // 10 second connection timeout
            if (socket.isConnected()) {
                socket.setSoTimeout(20 * 1000); // 20 second timeout once data is flowing
                is = socket.getInputStream();
                dis = new DataInputStream(is);
                os = socket.getOutputStream();
                dos = new DataOutputStream(os);

                String requestmsg = "GET ";
                if (flg == 0) {
                    requestmsg += "/ HTTP/1.0\r\n";
                } else {
                    requestmsg += "/RTKVRSRTCM3 HTTP/1.0\r\n";
                }
                requestmsg += "User-Agent: NTRIP BatcRTCMParser/1.0\r\n";
                requestmsg += "Accept: */*\r\n";
                requestmsg += "Connection: close\r\n";
                if (flg == 1) {
                    requestmsg += "Authorization: Basic " + ToBase64("nls:gnss2016");
//                    requestmsg += "Authorization: Basic " + ToBase64(username+":"+password);
                    requestmsg += "\r\n";
                }
                requestmsg += "\r\n";
                dos.write(requestmsg.getBytes());
                Log.i(TAG, "已向服务器发送数据");
                Intent intent1 = new Intent();
                intent1.setAction("ntripClientReceiver1");
                intent1.putExtra("string1", "已向服务器发送数据");
                LocalBroadcastManager.getInstance(MainActivity.context).sendBroadcast(intent1);


                byte[] buffer = new byte[4096];
                int read = dis.read(buffer, 0, 4096); // This is blocking
                while (read != -1) {
                    byte[] tempdata = new byte[read];
                    System.arraycopy(buffer, 0, tempdata, 0, read);
                    String str1 = new String(tempdata);
                    String str2 = str1.substring(0, 10);
                    Log.i(TAG, "接收到数据: " + str1);
                    Intent intent2 = new Intent();
                    intent2.setAction("ntripClientReceiver2");
                    intent2.putExtra("string2", str1);
                    LocalBroadcastManager.getInstance(MainActivity.context).sendBroadcast(intent2);

                    if (flg == 1) {

                        if (str2.equals("ICY 200 OK")) {
//                            gpgga=GenerateGGAFromLatLon(Lat,Lon);
                            gpgga = GenerateGGAFromLatLon(31.02503, 121.43926);
                            gpgga += "\r\n";
                            dos.write(gpgga.getBytes());
                            Log.i(TAG, "已发送GGA信息");
                            Intent intent3 = new Intent();
                            intent3.setAction("ntripClientReceiver3");
                            intent3.putExtra("string3", "已发送GGA信息");
                            LocalBroadcastManager.getInstance(MainActivity.context).sendBroadcast(intent3);
                        } else {
                            //String str3 = bytesToHex(tempdata);
                            int[] tmpData=byteToInt(tempdata);
                            String str3 = rtcm(tmpData);
                            refInfo = rtcm2(tmpData);
                            System.out.printf("HEX: %s\n", str3);
                            Intent intent4 = new Intent();
                            intent4.setAction("ntripClientReceiver4");
//                            intent4.putExtra("string4", str3);
                            intent4.putExtra("string4", refInfo);
                            LocalBroadcastManager.getInstance(MainActivity.context).sendBroadcast(intent4);
                        }

                    }
                    read = dis.read(buffer, 0, 4096); // This is blocking
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            is.close();
            dis.close();
            os.close();
            dos.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public RefInfo[] rtcm2 (int[] data){  //产生序列化实例对象
        RefInfo[] refInfo = new RefInfo[32] ;
        for(int i=0;i<32;i++){
            refInfo[i] = new RefInfo();
        }
        int len=data.length;
        String s = "";
        for(int i=0;i<len;i++){
            if(data[i]==211){
                double infoLen=getValue(data,8*i+14,8*i+24);
                int tmpData[]= Arrays.copyOfRange(data,i,i+(int)infoLen+6);
                //if(crc24(tmpData)) {
                int msgType=getValue(tmpData,24,36);
                switch(msgType) {
                    case 1004:
                        double referStation = getValue(tmpData, 36, 48);
                        double TOW = 0.001 * getValue(tmpData, 48, 78);
                        double SGFlag = getValue(tmpData, 78, 79);
                        int numofSat = getValue(tmpData, 79, 84);
                        double DSI = getValue(tmpData, 84, 85);
                        double SI = getValue(tmpData, 85, 88);
                        s = s + "信息长度:" + infoLen + "\r\n";
                        s = s + "信息类型:" + msgType + "\r\n";
                        s = s + "参考站id:" + referStation + "\r\n";
                        s = s + "TOW:" + TOW + "\r\n";
                        s = s + "Synchronous GNSS Flag:" + SGFlag + "\r\n";
                        s = s + "卫星数量:" + numofSat + "\r\n";
                        s = s + "无散平滑指标:" + DSI + "\r\n";
                        s = s + "平滑区间:" + SI + "\r\n";
                        //下面对各个卫星信息分别解码
                        for (int j = 0; j < numofSat; j++) {
                            int start = 88 + 125 * j;
                            int satID = getValue(tmpData, start, start + 6);
                            double pseL1 = getValue(tmpData, start + 7, start + 31);
                            double PRPseL1=0.0005*getValue(tmpData,start+31,start+51);
                            double PMAL1 = getValue(tmpData, start + 58, start + 66);
                            double CNRL1=getValue(tmpData,start+66,start+74)*0.25;
                            double PSEL1 = pseL1 * 0.02 + PMAL1 * 299792.458;
                            double PRL1=(PRPseL1+PSEL1)/lam;
                            s=s+"卫星ID:" + satID + "\r\nL1伪距:" + PSEL1 + "\r\nL1载波相位:"+PRL1+"\r\nL1载噪比:"+CNRL1+"\r\n";
                            refInfo[satID-1].setSvid(satID);
                            refInfo[satID-1].setRefPr(PSEL1);
                            refInfo[satID-1].setRefPhase(PRL1);
                            double PSEL2=getValue(tmpData,start+76,start+90)*0.02+PSEL1;
                            double PRL2=(getValue(tmpData,start+90,start+110)*0.0005+PSEL1)/lam;
                            double CNRL2=getValue(tmpData,start+117,start+125)*0.25;
                            s=s+"L2伪距:"+PSEL2+"\r\nL2载波相位:"+PRL2+"\r\nL2载噪比:"+CNRL2+"\r\n";
                        }
                        break;
                    case 1006:
                        int referStaID=getValue(tmpData,36,48);
                        double ITRFRealiYear=getValue(tmpData,48,54);
                        double AntennaECEFX=0.0001*getInt38(tmpData,58);
                        double AntennaECEFY=0.0001*getInt38(tmpData,98);
                        double AntennaECEFZ=0.0001*getInt38(tmpData,138);
                        double AntennaHeight=0.0001*getInt38(tmpData,176);
                        s=s+"参考站ID:"+referStaID+"\r\n";
                        s=s+"天线参考点ECEF-X:"+AntennaECEFX+"\r\n";
                        s=s+"天线参考点ECEF-Y:"+AntennaECEFY+"\r\n";
                        s=s+"天线参考点ECEF-Z:"+AntennaECEFZ+"\r\n";
                        s=s+"天线参考点ECEFHeight:"+AntennaHeight+"\r\n";
                        break;
                    default:
                        break;
                }
                i = i + (int) infoLen + 5;//i++会再加一次
                //}
                //else s=s+"Package Error\r\n";
            }
        }
        return refInfo;
    }

    public String rtcm(int[] data){
        int len=data.length;
        String s = "";
        for(int i=0;i<len;i++){
            if(data[i]==211){
                double infoLen=getValue(data,8*i+14,8*i+24);
                int tmpData[]= Arrays.copyOfRange(data,i,i+(int)infoLen+6);
                //if(crc24(tmpData)) {
                int msgType=getValue(tmpData,24,36);
                switch(msgType) {
                    case 1004:
                        double referStation = getValue(tmpData, 36, 48);
                        double TOW = 0.001 * getValue(tmpData, 48, 78);
                        double SGFlag = getValue(tmpData, 78, 79);
                        int numofSat = getValue(tmpData, 79, 84);
                        double DSI = getValue(tmpData, 84, 85);
                        double SI = getValue(tmpData, 85, 88);
                        s = s + "信息长度:" + infoLen + "\r\n";
                        s = s + "信息类型:" + msgType + "\r\n";
                        s = s + "参考站id:" + referStation + "\r\n";
                        s = s + "TOW:" + TOW + "\r\n";
                        s = s + "Synchronous GNSS Flag:" + SGFlag + "\r\n";
                        s = s + "卫星数量:" + numofSat + "\r\n";
                        s = s + "无散平滑指标:" + DSI + "\r\n";
                        s = s + "平滑区间:" + SI + "\r\n";
                        //下面对各个卫星信息分别解码
                        for (int j = 0; j < numofSat; j++) {
                            int start = 88 + 125 * j;
                            double satID = getValue(tmpData, start, start + 6);
                            double pseL1 = getValue(tmpData, start + 7, start + 31);
                            double PRPseL1=0.0005*getValue(tmpData,start+31,start+51);
                            double PMAL1 = getValue(tmpData, start + 58, start + 66);
                            double CNRL1=getValue(tmpData,start+66,start+74)*0.25;
                            double PSEL1 = pseL1 * 0.02 + PMAL1 * 299792.458;
                            double PRL1=(PRPseL1+PSEL1)/lam;
                            s=s+"卫星ID:" + satID + "\r\nL1伪距:" + PSEL1 + "\r\nL1载波相位:"+PRL1+"\r\nL1载噪比:"+CNRL1+"\r\n";
                            double PSEL2=getValue(tmpData,start+76,start+90)*0.02+PSEL1;
                            double PRL2=(getValue(tmpData,start+90,start+110)*0.0005+PSEL1)/lam;
                            double CNRL2=getValue(tmpData,start+117,start+125)*0.25;
                            s=s+"L2伪距:"+PSEL2+"\r\nL2载波相位:"+PRL2+"\r\nL2载噪比:"+CNRL2+"\r\n";
                        }
                        break;
                    case 1006:
                        int referStaID=getValue(tmpData,36,48);
                        double ITRFRealiYear=getValue(tmpData,48,54);
                        double AntennaECEFX=0.0001*getInt38(tmpData,58);
                        double AntennaECEFY=0.0001*getInt38(tmpData,98);
                        double AntennaECEFZ=0.0001*getInt38(tmpData,138);
                        double AntennaHeight=0.0001*getInt38(tmpData,176);
                        s=s+"参考站ID:"+referStaID+"\r\n";
                        s=s+"天线参考点ECEF-X:"+AntennaECEFX+"\r\n";
                        s=s+"天线参考点ECEF-Y:"+AntennaECEFY+"\r\n";
                        s=s+"天线参考点ECEF-Z:"+AntennaECEFZ+"\r\n";
                        s=s+"天线参考点ECEFHeight:"+AntennaHeight+"\r\n";
                        break;
                    default:
                        break;
                }
                i = i + (int) infoLen + 5;//i++会再加一次
                //}
                //else s=s+"Package Error\r\n";
            }
        }
        return s;
    }
    //获取16进制数低n位的十进制值
    public int getLowValue(long num,int bitNum){
        long result=0;
        for(int i=0;i<bitNum;i++){
            result=result+(num%2)*(long)Math.pow(2,i);
            num=num/(long)2;
        }
        return (int)result;
    }
    //true校验通过，否则不通过
    public boolean crc24(int[] data){
        int len=data.length;
        int a=0;
        for(int i=0;i<len;i++){
            a=(a*256+data[i])%gx;
        }
        return a==0;
    }
    //获取十进制数组对应的二进制中从fr到to（单位：bit）范围的十进制值，包括fr，不包括to
    public int getValue(int[] data,int fr,int to){
        int[] data1=new int[data.length+1];
        for(int j=0;j<data.length;j++){
            data1[j]=data[j];
        }
        data1[data.length]=0;//向数组末尾增加一个0将数组扩容，避免下标溢出的情况
        int start=fr/8;int stop=to/8;
        int dataN=stop-start;
        long result1=0;
        for(int i=0;i<dataN+1;i++){
            result1=result1+(long)data1[stop-i]*(long)Math.pow(2,8*i);
        }
        int result=getLowValue(result1/(long)Math.pow(2,8-to%8),to-fr);
        return result;
    }

    public long getInt38(int[] data,int fr){
        long data0,data1,data2;
        data0=(long)getValue(data,fr,fr+1);
        data1=(long)getValue(data,fr+1,fr+20);
        data2=(long)getValue(data,fr+20,fr+38);
        return -data0*(long)Math.pow(2,37)+data1*(long)Math.pow(2,18)+data2;
    }

    private int[] byteToInt(byte[] bytes){
        int len=bytes.length;
        int[] tmp=new int[4096];
        for(int i=0;i<len;i++){
            tmp[i]=bytes[i]&0xFF;
        }
        return tmp;
    }
    private String ToBase64(String in) {
        return Base64.encodeToString(in.getBytes(), 4);
    }

    private String GenerateGGAFromLatLon(double ManualLat,double ManualLon) {
        String gga = "GPGGA,073001,";

        double posnum = Math.abs(ManualLat);
        double latmins = posnum % 1;
        int ggahours = (int)(posnum - latmins);
        latmins = latmins * 60;
        double latfracmins = latmins % 1;
        int ggamins = (int)(latmins - latfracmins);
        int ggafracmins = (int)(latfracmins * 10000);
        ggahours = ggahours * 100 + ggamins;
        if (ggahours < 1000) {
            gga += "0";
            if (ggahours < 100) {
                gga += "0";
            }
        }
        gga += ggahours + ".";
        if (ggafracmins < 1000) {
            gga += "0";
            if (ggafracmins < 100) {
                gga += "0";
                if (ggafracmins < 10) {
                    gga += "0";
                }
            }
        }
        gga += ggafracmins;
        if (ManualLat > 0) {
            gga += ",N,";
        } else {
            gga += ",S,";
        }

        posnum = Math.abs(ManualLon);
        latmins = posnum % 1;
        ggahours = (int)(posnum - latmins);
        latmins = latmins * 60;
        latfracmins = latmins % 1;
        ggamins = (int)(latmins - latfracmins);
        ggafracmins = (int)(latfracmins * 10000);
        ggahours = ggahours * 100 + ggamins;
        if (ggahours < 10000) {
            gga += "0";
            if (ggahours < 1000) {
                gga += "0";
                if (ggahours < 100) {
                    gga += "0";
                }
            }
        }
        gga += ggahours + ".";
        if (ggafracmins < 1000) {
            gga += "0";
            if (ggafracmins < 100) {
                gga += "0";
                if (ggafracmins < 10) {
                    gga += "0";
                }
            }
        }
        gga += ggafracmins;
        if (ManualLon > 0) {
            gga += ",E,";
        } else {
            gga += ",W,";
        }

        gga += "1,8,1,0,M,-32,M,3,0";

        String checksum = CalculateChecksum(gga);

        return "$" + gga + "*" + checksum;
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private String CalculateChecksum(String line) {
        int chk = 0;
        for (int i = 0; i < line.length(); i++) {
            chk ^= line.charAt(i);
        }
        String chk_s = Integer.toHexString(chk).toUpperCase(Locale.ENGLISH); // convert the integer to a HexString in upper case
        while (chk_s.length() < 2) { // checksum must be 2 characters. if it falls short, add a zero before the checksum
            chk_s = "0" + chk_s;
        }
        return chk_s;
    }

}

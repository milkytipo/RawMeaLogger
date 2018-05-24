package com.example.a2017101705.rawmealogger;
import android.app.Activity;
import android.Manifest;
import android.app.Service;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AlertDialog;
import android.os.Bundle;
import android.location.Criteria;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssClock;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.OnNmeaMessageListener;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.support.v4.content.ContextCompat;
import android.content.DialogInterface;
import android.net.Uri;

import org.w3c.dom.Text;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class MainActivity extends AppCompatActivity  {

    private LocationManager lm;
    private String measurementStream;
    private Button start,end,clear,startLog,stopLog,on;
    private TextView logView;
    private ScrollView scrollView;
    private static final String TAG = "GpsActivity";
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
    };
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    private boolean hasPermission;
    private boolean isDislay=false;
    private static final int LOCATION_REQUEST_ID = 1;
    private static final int WriteAndRead_REQUEST_ID = 2;
    private StringBuilder builder,builder1;
    private String UTCtime;
    private double GPStime = 0;  //周内秒
    private Ephemeris eph;
    private int  count = 0;
    private int  cntDB = 0;
    private boolean doWrite = false;
    private boolean closeWrite = false;
    private MyDatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        start=findViewById(R.id.start);
        end=findViewById(R.id.end);
        clear=findViewById(R.id.clear);
        startLog=findViewById(R.id.start_log);
        stopLog=findViewById(R.id.stop_log);
        on=findViewById(R.id.on_switch);
        logView = (TextView)findViewById(R.id.log_view);
        scrollView= findViewById(R.id.log_scroll);
        builder = new StringBuilder("");
        builder1 = new StringBuilder("");
        dbHelper = new MyDatabaseHelper(this,"NAVStore.db",null,1);

        start.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        scrollView.fullScroll(View.FOCUS_UP);
                    }
                });

        end.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        scrollView.fullScroll(View.FOCUS_DOWN);
                    }
                });

        clear.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        logView.setText("");
                    }
                });
        startLog.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        doWrite = true;
                        closeWrite =false;
                        Toast.makeText(getApplicationContext(),"start log ",Toast.LENGTH_SHORT).show();
                        dbHelper.getWritableDatabase();
                    }
                });
        stopLog.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
//                        doWrite = false;
                        closeWrite= true;
                        Toast.makeText(getApplicationContext(),"save log ",Toast.LENGTH_SHORT).show();
                    }
                });
        on.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Vibrator vibrator=(Vibrator)getSystemService(Service.VIBRATOR_SERVICE);
                vibrator.vibrate(new long[]{0,100}, -1);
                if(count == 0){
                    isDislay = true;
                    Toast.makeText(getApplicationContext()," on ",Toast.LENGTH_SHORT).show();
                    on.setActivated(true);
                    count = 1;
                }else if(count == 1) {
                    isDislay = false;
                    Toast.makeText(getApplicationContext(), " stop", Toast.LENGTH_SHORT).show();
                    on.setActivated(false);
                    count = 0;
                }
                new  Thread(new Runnable() {
                    @Override
                    public void run() {
                        mTimeHandler.sendEmptyMessageDelayed(0, 800);
                    }
                }).start();

            }
        });

        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // 判断GPS是否正常启动
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "请开启GPS开关", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivityForResult(intent, 0);
            hasPermission=false;
            return;
        }
        String bestProvider = lm.getBestProvider(getCriteria(), true);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, LOCATION_REQUEST_ID);

        }
        else
            hasPermission=true;
        if(hasPermission)
        {
            lm.registerGnssMeasurementsCallback(gnssMeasurementsEventListener);
            lm.registerGnssNavigationMessageCallback(gnssNavigationMessageListener);

        }


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case 1:
                if (resultCode == RESULT_OK) {
                    String returnedData = data.getStringExtra("data_return");
                    Log.d("MainActivity", returnedData);
                }
                break;
            default:
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        lm.unregisterGnssMeasurementsCallback(gnssMeasurementsEventListener);
    }

    /*
    6.0以上的各种权限权限
     */
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST_ID) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!(shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) & shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION))) {
                    AskForPermission();
                }
            }
        }
        if (requestCode == WriteAndRead_REQUEST_ID) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!(shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) & shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION))) {
                    AskForPermission();
                }
            }
        }
    }
    private void AskForPermission() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Need Permission!");
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        builder.setPositiveButton("Settings", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivityForResult(intent, 0);
                hasPermission=true;

            }
        });
        builder.create().show();
    }

    /*
    位置监听
     */
    private LocationListener locationListener = new LocationListener() {

        public void onLocationChanged(Location location) {

            updateView(location);
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {

        }
        public void onProviderEnabled(String provider) {

        }

        public void onProviderDisabled(String provider) {
            updateView(null);
        }

    };
        private void updateView(Location location) {   //不能再用logview
            if (location != null) {
                logView.setText("设备位置信息\n ");
                logView.setText("经度: "+ String.valueOf(location.getLongitude()));
                logView.setText("纬度: "+String.valueOf(location.getLatitude()));
            }
        }

    /*
    Handler 处理
     */
    Handler mTimeHandler = new Handler(){
        public void handleMessage(Message msg){
            switch (msg.what){
                case 0:
                    if(isDislay) {
                        logView.setText(String.format("%s", builder.toString()));
                        logView.append(String.format("%s", builder1.toString()));

                    }
                    sendEmptyMessageDelayed(0, 800);     //数据大概1s刷新一次，所以设置为0.8
                    break;
            }
        }
    };

    private final GnssMeasurementsEvent.Callback gnssMeasurementsEventListener = new GnssMeasurementsEvent.Callback() {
                @Override
                public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
                    builder = new StringBuilder(" \n");
                    final String format = ("%-4s %s");
                    GnssClock gnssClock = event.getClock();
                    UTCtime = getTime(gnssClock).UTCtime;
//                    builder.append(UTCtime + "\n");
                    GPStime =getTime(gnssClock).TowSec;
//                    builder.append(GPStime+ "\n");
                    Time_Args time = new Time_Args();
                    time = getTime(gnssClock);
                    double LLI,strength;  //失锁标志位和信号强度
                    int cnt = 0;
                    for (GnssMeasurement measurement : event.getMeasurements()) {
                        if (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GPS  ){
                            cnt++;
//                            builder.append(toStringMeasurement(measurement));   //显示卫星原始GNSS观测量
//                            builder.append(toStringClock(event.getClock()));    //显示卫星原始clock观测量
//                            builder.append(String.format(format,"cnt   ",cnt+"\n"));
                            builder.append(String.format(format, "constellation is ", String.valueOf(getGNSSObstype(measurement) + "\n")));
                            builder.append(String.format(format, "pseudorange is ", String.valueOf(getPseudorange(measurement, gnssClock)) + "\n"));
                            builder.append(String.format(format, "Doppler is ", String.valueOf(getDoppler(measurement) + "\n")));
                            builder.append(String.format(format, "CNo is ", String.valueOf(getCN0(measurement) + "\n")));
                            builder.append(String.format(format, "ADR ", measurement.getAccumulatedDeltaRangeMeters() + "\n"));
                            builder.append(String.format(format, "ADRflag ", measurement.getAccumulatedDeltaRangeState() + "\n"));
                            builder.append(String.format(format, "周内秒 is ", getTime(gnssClock).TowSec + "\n\n"));
                            builder.append(String.format(format, "L1phase is ", getL1phase(measurement) + "\n\n"));
                            builder.append("----------------------\n");
                            if (doWrite) {
                                String fileName = String.format("%s_%s.txt", "gnssMeasurement", getGNSSObstype(measurement));
//                                String measurementStream = String.format("%s,%s,%s,%s",getTime(gnssClock).UTCtime,getGNSSObstype(measurement), getPseudorange(measurement, gnssClock), getL1phase(measurement))+"\n";
                                String measurementStream = String.format("%s,%s,%s,%s",getTime(gnssClock).UTCtime,getGNSSObstype(measurement),getPseudorange(measurement,gnssClock),getL1phase(measurement))+"\n";
                                writeToFile(fileName, measurementStream);
                            }
                            String measurementStream2 = String.format("%s  %14.3f  %14.3f  %14.3f  %14.3f\n",getGNSSObstype(measurement), getPseudorange(measurement, gnssClock), getL1phase(measurement),
                            getCN0(measurement),getDoppler(measurement));
                            builder.append(measurementStream2);
                        }
                    }

                    builder.append(transRinex_Data(time,cnt));
                }
    @Override
    public void onStatusChanged(int status) {

        }
    };

    private final GnssNavigationMessage.Callback gnssNavigationMessageListener = new GnssNavigationMessage.Callback(){
        @Override
        public void onGnssNavigationMessageReceived(GnssNavigationMessage event) {
            if(cntDB == 0)
//                initialtDatabase();
            cntDB =1;
            builder1 = new StringBuilder("");
            String str=bytesToHex(event.getData());
            eph =new Ephemeris();
            eph.Svid =  event.getSvid();
            builder1.append(String.format("ID : %s", eph.Svid+"\n"));
            if(event.getType()==GnssNavigationMessage.TYPE_GPS_L1CA && event.getStatus()==GnssNavigationMessage.STATUS_PARITY_PASSED){
                if(event.getSubmessageId()==1){
                    ParseSubframe1(event.getData(),eph);
                    builder1.append(String.format("toc: %s\n", eph.toc));
                    builder1.append(String.format("af0: %s\n", eph.af0));
                    builder1.append(String.format("af1: %s\n", eph.af1));
                    builder1.append(String.format("af2: %s\n", eph.af2));
                    builder1.append(String.format("TGD: %s\n", eph.TGD));
                } if(event.getSubmessageId()==2){
                    ParseSubframe2(event.getData(),eph);
                    builder1.append(String.format("toe: %s\n", eph.toe));
                    builder1.append(String.format("sqrtA: %s\n", eph.sqrtA));
                    builder1.append(String.format("es: %s\n", eph.es));
                    builder1.append(String.format("M0: %s\n", eph.M0));
                    builder1.append(String.format("delta_n: %s\n", eph.delta_n));
                    builder1.append(String.format("Cuc: %s\n", eph.Cuc));
                    builder1.append(String.format("Cus: %s\n", eph.Cus));
                    builder1.append(String.format("Crs: %s\n", eph.Crs));
                } if(event.getSubmessageId()==3){
                    ParseSubframe3(event.getData(),eph);
                    builder1.append(String.format("i0: %s\n", eph.i0));
                    builder1.append(String.format("Omega0: %s\n", eph.Omega_0));
                    builder1.append(String.format("w: %s\n", eph.w));
                    builder1.append(String.format("IDOT: %s\n", eph.i_dot));
                    builder1.append(String.format("Omega_dot: %s\n", eph.Omega_dot));
                    builder1.append(String.format("Crc: %s\n", eph.Crc));
                    builder1.append(String.format("Cic: %s\n", eph.Cic));
                    builder1.append(String.format("Cis: %s\n", eph.Cis));
                }
                updateDatabase(eph);
                double[][] PVTofSat = calSatPos(eph,GPStime);
                builder1.append(String.format("PVT solution of GPS Satellite: \n  X_k : %s Y_k : %s Z_k : %s \n",PVTofSat[0][0],PVTofSat[0][1],PVTofSat[0][2]));
                String measurementStream_navRaw = String.format("%s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s \n",eph.toc,eph.af0,eph.af1,eph.af2,eph.TGD,eph.toe,eph.sqrtA,eph.es,eph.M0,eph.delta_n,eph.Cuc,eph.Cus,eph.Crs,eph.i0,eph.Omega_0,
                        eph.w,eph.i_dot,eph.Omega_dot,eph.Crc,eph.Cic,eph.Cis);
                String fileName_navRaw = String.format("%s_%s.txt", "rawNav", event.getSvid());
                String measurementStream_nav = String.format(" %s %s %s \n",PVTofSat[0][0],PVTofSat[0][1],PVTofSat[0][2]);
                String fileName_nav = String.format("%s_%s.txt", "PVTofNav", event.getSvid());
                writeToFile(fileName_nav,measurementStream_nav);
                writeToFile(fileName_navRaw,measurementStream_navRaw);
            }
        }
    };

    /*
    原始观测量结算部分
     */
    private String toStringMeasurement(GnssMeasurement measurement) {
        final String format = "   %s = %s\n";
        StringBuilder builder = new StringBuilder("GnssMeasurement:\n");
        DecimalFormat numberFormat = new DecimalFormat("#0.000");
        DecimalFormat numberFormat1 = new DecimalFormat("#0.000E00");
        builder.append(String.format(format, "Svid", measurement.getSvid()));
        builder.append(String.format(format, "ConstellationType", measurement.getConstellationType()));
        builder.append(String.format(format, "TimeOffsetNanos", measurement.getTimeOffsetNanos()));
        builder.append(String.format(format, "State", measurement.getState()));
        builder.append(
                String.format(format, "ReceivedSvTimeNanos", measurement.getReceivedSvTimeNanos()));
        builder.append(
                String.format(
                        format,
                        "ReceivedSvTimeUncertaintyNanos",
                        measurement.getReceivedSvTimeUncertaintyNanos()));

        builder.append(String.format(format, "Cn0DbHz", numberFormat.format(measurement.getCn0DbHz())));

        builder.append(
                String.format(
                        format,
                        "PseudorangeRateMetersPerSecond",
                        numberFormat.format(measurement.getPseudorangeRateMetersPerSecond())));
        builder.append(
                String.format(
                        format,
                        "PseudorangeRateUncertaintyMetersPerSeconds",
                        numberFormat.format(measurement.getPseudorangeRateUncertaintyMetersPerSecond())));

        if (measurement.getAccumulatedDeltaRangeState() != 0) {
            builder.append(
                    String.format(
                            format, "AccumulatedDeltaRangeState", measurement.getAccumulatedDeltaRangeState()));

            builder.append(
                    String.format(
                            format,
                            "AccumulatedDeltaRangeMeters",
                            numberFormat.format(measurement.getAccumulatedDeltaRangeMeters())));
            builder.append(
                    String.format(
                            format,
                            "AccumulatedDeltaRangeUncertaintyMeters",
                            numberFormat1.format(measurement.getAccumulatedDeltaRangeUncertaintyMeters())));
        }

        if (measurement.hasCarrierFrequencyHz()) {
            builder.append(
                    String.format(format, "CarrierFrequencyHz", measurement.getCarrierFrequencyHz()));
        }

        builder.append(
                String.format(format, "MultipathIndicator", measurement.getMultipathIndicator()));

        if (measurement.hasSnrInDb()) {
            builder.append(String.format(format, "SnrInDb", measurement.getSnrInDb()));
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (measurement.hasAutomaticGainControlLevelDb()) {
                builder.append(
                        String.format(format, "AgcDb", measurement.getAutomaticGainControlLevelDb()));
            }
            if (measurement.hasCarrierFrequencyHz()) {
                builder.append(String.format(format, "CarrierFreqHz", measurement.getCarrierFrequencyHz()));
            }
        }
        return builder.toString();
  }

    private Time_Args getTime(GnssClock gnssClock) {
//        ArrayList<String> list = new ArrayList<String>();
        Time_Args time = new Time_Args();
        int LeapSecond,year =0,month=0,hour=0,day=0,min=0,dayNum=0;
        long BiasNanos,UTCtimeNanos,GPStimeNanos,TimeNanos,FullBiasNanos;
        double UTCtimeSec,GPStimeSec,TowSec;
        String sec = "";
        boolean fail;
        String UTCtime  = new String(" ");
        DecimalFormat numberFormat = new DecimalFormat("#0.0000000");
        int[] nonLeapMonth = {31,59,90,120,151,181,212,243,273,304,334,365};
        int[] LeapMonth = {31,60,91,121,152,182,213,244,274,305,335,366};
        int leapyear;
        fail=false;
        TowSec=0;//周内秒
        FullBiasNanos=0;
        BiasNanos=0;
        TimeNanos=gnssClock.getTimeNanos();

        if (gnssClock.hasFullBiasNanos()) {
            FullBiasNanos=gnssClock.getFullBiasNanos();      //Q：这个针对非GPS星座有问题，因为非GPS星座用的时间系统不一样
        } else
            fail=true;
        if (gnssClock.hasBiasNanos()) {
            BiasNanos= (long) gnssClock.getBiasNanos();
        } else
            fail=true;

        if (fail == false) {
            if (gnssClock.hasLeapSecond()) {
                LeapSecond=gnssClock.getLeapSecond();
                UTCtimeNanos=TimeNanos-(FullBiasNanos+BiasNanos)-LeapSecond*1000000000;
                UTCtimeSec = UTCtimeNanos/1000000000;
            } else {
                GPStimeNanos=TimeNanos-(FullBiasNanos+BiasNanos);
                GPStimeSec = (double)GPStimeNanos/1000000000;     //这一步double转换导致了小数点后只有两位数字是有效的，所以rinex格式的时间后面是有问题的。
                UTCtimeSec = GPStimeSec - GPSConstants.getLeapSec();
            }
            TowSec = UTCtimeSec%(3600*24*7);
            UTCtimeSec += 6*24*60*60;
            dayNum = (int) Math.floor(UTCtimeSec/(3600*24));
            year =(int) Math.floor(dayNum/365);
            leapyear = (int)Math.floor(year/4)+1;      //100年的闰年问题让2100年的人去写吧
            month = (int)Math.floor(((dayNum - year*365 - leapyear))/30);
            day = dayNum -year*365 -leapyear -nonLeapMonth[month-1];
            hour = (int)Math.floor( (UTCtimeSec - dayNum*24*3600)/3600);
            min = (int)Math.floor((UTCtimeSec - dayNum*24*3600 -hour*3600)/60 );
            sec =numberFormat.format(UTCtimeSec - dayNum*24*3600 -hour*3600-min*60);
            year += 1980;
            month = month +1;
            UTCtime =(year+" "+month+" "+day+" "+hour+" "+min+" "+sec);
        } else
            UTCtimeSec=-1;
        time.year = year;
        time.month = month;
        time.day = day;
        time.hour =hour;
        time.min = min;
        time.sec = Double.parseDouble(sec);
        time.UTCtime = UTCtime;
        time.TowSec = TowSec;
        return time;
    }

    private String toStringClock(GnssClock gnssClock) {
        final String format = "   %-4s = %s\n";
        StringBuilder builder = new StringBuilder("GnssClock:\n");
        DecimalFormat numberFormat = new DecimalFormat("#0.000");
        if (gnssClock.hasLeapSecond()) {
            builder.append(String.format(format, "LeapSecond", gnssClock.getLeapSecond()));
        }

        builder.append(String.format(format, "TimeNanos", gnssClock.getTimeNanos()));
        if (gnssClock.hasTimeUncertaintyNanos()) {
            builder.append(
                    String.format(format, "TimeUncertaintyNanos", gnssClock.getTimeUncertaintyNanos()));
        }

        if (gnssClock.hasFullBiasNanos()) {
            builder.append(String.format(format, "FullBiasNanos", gnssClock.getFullBiasNanos()));
        }

        if (gnssClock.hasBiasNanos()) {
            builder.append(String.format(format, "BiasNanos", gnssClock.getBiasNanos()));
        }
        if (gnssClock.hasBiasUncertaintyNanos()) {
            builder.append(
                    String.format(
                            format,
                            "BiasUncertaintyNanos",
                            numberFormat.format(gnssClock.getBiasUncertaintyNanos())));
        }

        if (gnssClock.hasDriftNanosPerSecond()) {
            builder.append(
                    String.format(
                            format,
                            "DriftNanosPerSecond",
                            numberFormat.format(gnssClock.getDriftNanosPerSecond())));
        }

        if (gnssClock.hasDriftUncertaintyNanosPerSecond()) {
            builder.append(
                    String.format(
                            format,
                            "DriftUncertaintyNanosPerSecond",
                            numberFormat.format(gnssClock.getDriftUncertaintyNanosPerSecond())));
        }

        builder.append(
                String.format(
                        format,
                        "HardwareClockDiscontinuityCount",
                        gnssClock.getHardwareClockDiscontinuityCount()));

        return builder.toString();
    }

    private Criteria getCriteria() {
        Criteria criteria = new Criteria();
        // 设置定位精确度 Criteria.ACCURACY_COARSE比较粗略，Criteria.ACCURACY_FINE则比较精细
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        // 设置是否要求速度
        criteria.setSpeedRequired(false);
        // 设置是否允许运营商收费
        criteria.setCostAllowed(false);
        // 设置是否需要方位信息
        criteria.setBearingRequired(false);
        // 设置是否需要海拔信息
        criteria.setAltitudeRequired(false);
        // 设置对电源的需求
        criteria.setPowerRequirement(Criteria.POWER_LOW);
        return criteria;
    }

    private double getPseudorange(GnssMeasurement measurement,GnssClock gnssClock){
        long weekNumber,prSeconds_nanos;
        long weekNumberNanos,tRxNanos;
        long tRxSeconds_nanos = 0;
        long tTxNanos= 0;
        double pseudorange = 0;
        if (gnssClock.hasFullBiasNanos()) {
            weekNumber = (long) Math.floor(-(double)gnssClock.getFullBiasNanos()*(Math.pow(10,-9)) / GPSConstants.getWeeksec());
            weekNumberNanos = (long) ((weekNumber)* ((GPSConstants.getWeeksec()))*Math.pow(10,9));
            tRxNanos = gnssClock.getTimeNanos() - gnssClock.getFullBiasNanos();
            tTxNanos = measurement.getReceivedSvTimeNanos();
            tRxSeconds_nanos = tRxNanos-weekNumberNanos;
            prSeconds_nanos = tRxSeconds_nanos - tTxNanos;
            if(prSeconds_nanos<0)
                prSeconds_nanos += GPSConstants.getWeeksec()*Math.pow(10,9);
            pseudorange = prSeconds_nanos * 0.299792458;
        }
        return pseudorange;

    }

    private String getGNSSObstype(GnssMeasurement measurement){
        String mGNSStype = "";
        String format1 = ("%s%02d");
        switch (measurement.getConstellationType()){
            case 0:
                mGNSStype = "#"; //unknown
                break;
            case 1:
                mGNSStype = "G";//GPS
                break;
            case 2:
                mGNSStype ="S";//SBAS
                break;
            case 3:
                mGNSStype = "R";//GLONASS
                break;
            case 4:
                mGNSStype = "J";//QZSS
                break;
            case 5:
                mGNSStype = "C";//BDS
                break;
            case 6:
                mGNSStype = "E";//Galileo
                break;
        }
        mGNSStype = String.format(format1,mGNSStype,measurement.getSvid());
//        mGNSStype = String.format(format1,mGNSStype);
        return  mGNSStype;
    }

    private double getDoppler(GnssMeasurement measurement){   //此部分的实际有问题
        long carrierFrequency;
        double pseudorangeRate;
        double Doppler;
        if(measurement.hasCarrierFrequencyHz()){
            carrierFrequency = (long) measurement.getCarrierFrequencyHz();
        }else{
            carrierFrequency = GPSConstants.getGpsL1Freq();  //carrierfreq获取不到，所以只能假设L1
        }
        pseudorangeRate = measurement.getPseudorangeRateMetersPerSecond();   //这个值也需要校正
        Doppler = (-(double)carrierFrequency/GPSConstants.getLightspeed())*pseudorangeRate;
        return  Doppler;
    }

    private  double getCN0(GnssMeasurement measurement){
        double CN0;
        CN0 =measurement.getCn0DbHz();
        return  CN0;

    }

    private String getSNR(GnssMeasurement measurement){
        String SNR = String.valueOf(-1);
        if(measurement.hasSnrInDb()){
            SNR = String.valueOf(measurement.getSnrInDb());
        }
        return SNR;
    }

    private double getL1phase(GnssMeasurement measurement){
        double ADR;
        long carrierFrequency;
        double L1Phase =-1;
        int ADRState;
//        DecimalFormat numberFormat = new DecimalFormat("#0.000");
        ADR =measurement.getAccumulatedDeltaRangeMeters();
        ADRState = measurement.getAccumulatedDeltaRangeState();   //返回的数值代表着跟踪状况
        carrierFrequency = -1*GPSConstants.getGpsL1Freq();
        L1Phase =-ADR*((double)carrierFrequency/GPSConstants.getLightspeed());
        return L1Phase;
    }

    private boolean dataFilter(GnssMeasurement measurement,GnssClock gnssClock){
        boolean valid = false;
        double pr = getPseudorange(measurement, gnssClock);
        if (measurement.getReceivedSvTimeUncertaintyNanos()<500  && measurement.getPseudorangeRateUncertaintyMetersPerSecond() <10 && pr>1e7 && pr<3e7)
            valid =true;
        else
            valid =false;

        return valid;
    }
    /*
    星历解算部分
     */
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private void ParseSubframe1(byte[] binStream,Ephemeris eph){
        int[] temp = new int[40];
        for(int i=0;i<40;i++)
        {
            temp[i]=binStream[i]&0xff;
        }

        eph.toc=(getLSB(temp[29],6)*65536+temp[30]*256+temp[31])/4;//consider the scale factor,unit seconds
        eph.af0=(-getBit(temp[36],5)*Math.pow(2,21)+getLSB(temp[36],5)*65536+temp[37]*256+temp[38])*Math.pow(2,-31);
        eph.af1=((-getBit(temp[33],5)*Math.pow(2,15)+getLSB(temp[33],5)*65536+temp[34]*256+temp[35])/64)*Math.pow(2,-43);
        eph.af2=((-getBit(temp[32],5)*Math.pow(2,7)+getLSB(temp[32],5)*256+temp[33])/64)*Math.pow(2,-55);
        eph.TGD=((-getBit(temp[26],5)*Math.pow(2,7)+getLSB(temp[26],5)*256+temp[27])/64)*Math.pow(2,-31);

    }

    private void ParseSubframe2(byte[] binStream,Ephemeris eph){

        int[] temp = new int[40];
        for(int i=0;i<40;i++)
        {
            temp[i]=binStream[i]&0xff;
        }

        eph.toe=(temp[36]*65536+temp[37]*256+temp[38])/4;//consider the scale factor,unit seconds
        eph.sqrtA=((getLSB(temp[30],6)*256+temp[31])*262144+(temp[32]*16777216+temp[33]*65536+temp[34]*256+temp[35])/64)*Math.pow(2,-19);
        eph.es=((getLSB(temp[22],6)*256+temp[23])*262144+(temp[24]*16777216+temp[25]*65536+temp[26]*256+temp[27])/64)*Math.pow(2,-33);
        eph.M0=(-getBit(temp[14],5)*Math.pow(2,31)+(getLSB(temp[14],5)*256+temp[15])*262144+(temp[16]*16777216+temp[17]*65536+temp[18]*256+temp[19])/64)*Math.pow(2,-31);
        eph.delta_n=(-getBit(temp[12],5)*Math.pow(2,15)+(getLSB(temp[12],5)*65536+temp[13]*256+temp[14])/64)*Math.pow(2,-43);
        eph.Cuc=(-getBit(temp[20],5)*Math.pow(2,15)+(getLSB(temp[20],5)*65536+temp[21]*256+temp[22])/64)*Math.pow(2,-29);
        eph.Cus=(-getBit(temp[28],5)*Math.pow(2,15)+(getLSB(temp[28],5)*65536+temp[29]*256+temp[30])/64)*Math.pow(2,-29);
        eph.Crs=(-getBit(temp[9],5)*Math.pow(2,15)+(getLSB(temp[9],5)*65536+temp[10]*256+temp[11])/64)*Math.pow(2,-5);

    }

    private void ParseSubframe3(byte[] binStream,Ephemeris eph){

        int[] temp = new int[40];
        for(int i=0;i<40;i++)
        {
            temp[i]=binStream[i]&0xff;
        }

        eph.Omega_0=(-getBit(temp[10],5)*Math.pow(2,31)+(getLSB(temp[10],5)*256+temp[11])*262144+(temp[12]*16777216+temp[13]*65536+temp[14]*256+temp[15])/64)*Math.pow(2,-31);
        eph.i0=(-getBit(temp[18],5)*Math.pow(2,31)+(getLSB(temp[18],5)*256+temp[19])*262144+(temp[20]*16777216+temp[21]*65536+temp[22]*256+temp[23])/64)*Math.pow(2,-31);
        eph.w=(-getBit(temp[26],5)*Math.pow(2,31)+(getLSB(temp[26],5)*256+temp[27])*262144+(temp[28]*16777216+temp[29]*65536+temp[30]*256+temp[31])/64)*Math.pow(2,-31);
        eph.Omega_dot=(-getBit(temp[32],5)*Math.pow(2,23)+(getLSB(temp[32],5)*16777216+temp[33]*65536+temp[34]*256+temp[35])/64)*Math.pow(2,-43);
        eph.i_dot=(-getBit(temp[37],5)*Math.pow(2,13)+(getLSB(temp[37],5)*256+temp[38]))*Math.pow(2,-43);
        eph.Crc=(-getBit(temp[24],5)*Math.pow(2,15)+(getLSB(temp[24],5)*65536+temp[25]*256+temp[26])/64)*Math.pow(2,-5);
        eph.Cic=(-getBit(temp[8],5)*Math.pow(2,15)+(getLSB(temp[8],5)*65536+temp[9]*256+temp[10])/64)*Math.pow(2,-29);
        eph.Cis=(-getBit(temp[16],5)*Math.pow(2,15)+(getLSB(temp[16],5)*65536+temp[17]*256+temp[18])/64)*Math.pow(2,-29);

    }

    private int getLSB(int in,int len){
        byte bin;
        int out;

        bin=(byte)in;
        switch(len){
            case 1:out=bin&0x01;break;
            case 2:out=bin&0x03;break;
            case 3:out=bin&0x07;break;
            case 4:out=bin&0x0F;break;
            case 5:out=bin&0x1F;break;
            case 6:out=bin&0x3F;break;
            case 7:out=bin&0x7F;break;
            case 8:out=bin&0xFF;break;
            default:out=0;break;
        }

        return out;
    }

    private int getBit(int in,int index){
        byte bin;
        int out;
        bin=(byte)in;
        switch(index){
            case 0:out=bin&0x01;break;
            case 1:out=bin&0x02;break;
            case 2:out=bin&0x04;break;
            case 3:out=bin&0x08;break;
            case 4:out=bin&0x10;break;
            case 5:out=bin&0x20;break;
            case 6:out=bin&0x40;break;
            case 7:out=bin&0x80;break;
            default:out=0;break;
        }

        return out;
    }

    private double[][] calSatPos(Ephemeris eph, double TowSec) {
        final double GM_WGS84 = 3986005e+8;
        final double omega_e_WGS84 = 7.2921151467e-5;
        final double pi = (float) 3.1415926;
        double[][] PVTofSat = new double[2][3];
        double n_0, n, t_k, M_k, E_k, E_k1, v_k, phi_k, C_u, C_r, C_i, u_k, r_k, i_k, x_k, y_k, OMEGA_k, X_k, Y_k, Z_k, M_k_dot, E_k_dot, v_k_dot, phi_k_dot, C_i_dot, C_r_dot, C_u_dot, u_k_dot, r_k_dot, i_k_dot, OMEGA_k_dot, x_k_dot, y_k_dot, X_k_dot, Y_k_dot, Z_k_dot;
        n_0 = Math.sqrt(GM_WGS84) / (eph.sqrtA * eph.sqrtA * eph.sqrtA);
        n = n_0 + eph.delta_n;
        t_k = TowSec - eph.toe;
        if (t_k > 302400)
            t_k = t_k - 604800;
        if (t_k < -302400)
            t_k = t_k + 604800;
        M_k = eph.M0 + n * t_k + 2 * pi;
        E_k = M_k;
        E_k1 = M_k + eph.es * Math.sin(E_k);
        while (Math.abs(E_k - E_k1) > 1e-9)
            E_k1 = E_k;
        E_k = M_k + eph.es * Math.sin(E_k1);
        v_k = 2 * Math.atan(Math.sqrt((1 + eph.es) / (1 - eph.es)) * Math.tan(E_k / 2));
        phi_k = v_k + eph.w;
        C_u = eph.Cus * Math.sin(2 * phi_k) + eph.Cuc * Math.cos(2 * phi_k);
        C_r = eph.Crs * Math.sin(2 * phi_k) + eph.Crc * Math.cos(2 * phi_k);
        C_i = eph.Cis * Math.sin(2 * phi_k) + eph.Cic * Math.cos(2 * phi_k);

        u_k = phi_k + C_u;
        r_k = eph.sqrtA * eph.sqrtA * (1 - eph.es * Math.cos(E_k)) + C_r;
        i_k = eph.i0 + eph.i_dot * t_k + C_i;
        x_k = r_k * Math.cos(u_k);
        y_k = r_k * Math.sin(u_k);
        OMEGA_k = eph.Omega_0 + (eph.Omega_dot - omega_e_WGS84) * t_k - omega_e_WGS84 * eph.toe;
        X_k = x_k * Math.cos(OMEGA_k) - y_k * Math.cos(i_k) * Math.sin(OMEGA_k);
        Y_k = x_k * Math.sin(OMEGA_k) + y_k * Math.cos(i_k) * Math.cos(OMEGA_k);
        Z_k = y_k * Math.sin(i_k);
        M_k_dot = n;
        E_k_dot = M_k_dot / (1 - eph.es * Math.cos(E_k));
        v_k_dot = Math.sqrt(1 - eph.es * eph.es) * E_k_dot / (1 - eph.es * Math.cos(E_k));
        phi_k_dot = v_k_dot;
        C_i_dot = 2 * phi_k_dot * (eph.Cis * Math.cos(2 * phi_k) - eph.Cic * Math.sin(2 * phi_k));

        C_r_dot = 2 * phi_k_dot * (eph.Crs * Math.cos(2 * phi_k) - eph.Crc * Math.sin(2 * phi_k));

        C_u_dot = 2 * phi_k_dot * (eph.Cus * Math.cos(2 * phi_k) - eph.Cuc * Math.sin(2 * phi_k));
        u_k_dot = phi_k_dot + C_u_dot;

        r_k_dot = eph.sqrtA * eph.sqrtA * eph.es * E_k_dot * Math.sin(E_k) + C_r_dot;

        i_k_dot = eph.i_dot + C_i_dot;

        OMEGA_k_dot = eph.Omega_dot - omega_e_WGS84;
        x_k_dot = r_k_dot * Math.cos(u_k) - r_k * u_k_dot * Math.sin(u_k);

        y_k_dot = r_k_dot * Math.sin(u_k) + r_k * u_k_dot * Math.cos(u_k);

        X_k_dot = x_k_dot * Math.cos(OMEGA_k) - Y_k * OMEGA_k_dot - (y_k_dot * Math.cos(i_k) - Z_k * i_k_dot) * Math.sin(OMEGA_k);

        Y_k_dot = x_k_dot * Math.sin(OMEGA_k) + X_k * OMEGA_k_dot + (y_k_dot * Math.cos(i_k) - Z_k * i_k_dot) * Math.cos(OMEGA_k);

        Z_k_dot = y_k_dot * Math.sin(i_k) + y_k * i_k_dot * Math.cos(i_k);

        PVTofSat[0][0] = X_k;
        PVTofSat[0][1] = Y_k;
        PVTofSat[0][2] = Z_k;
        PVTofSat[1][0] = X_k_dot;
        PVTofSat[1][1] = Y_k_dot;
        PVTofSat[1][2] = Z_k_dot;

        return PVTofSat;
    }
    /*
    格式转换/数据库/文件读写部分
     */
    private String transRinex_Data(Time_Args time, int numOfSat){
        String identifier =  "<";
        String message = "";
        int ephochFlag = 0;
        double offSet = 0;
        message = String.format("%s %4d %2d% 2d% 2d% 2d%11.7f  %1d%3d       \n",
                identifier,time.year,time.month,time.day,time.hour,time.min,time.sec,ephochFlag,numOfSat);

        return  message;

    }

    private void writeToFile(String fileName,String message) {

        FileOutputStream out = null;
        BufferedWriter writer =null;
        File baseDirectory;
        baseDirectory = new File(Environment.getExternalStorageDirectory(), "GNSSMeasurement");
        baseDirectory.mkdirs();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, WriteAndRead_REQUEST_ID);
        }else {
            try {
                File file = new File(baseDirectory, fileName);
                out = new FileOutputStream(file,true);
                writer = new BufferedWriter(new OutputStreamWriter(out));
                writer.write(message);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (writer != null) {
                        writer.flush();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void insertDatabase(Ephemeris eph){
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("Svid",eph.Svid);

        values.put("af0",eph.af0);
        values.put("af1",eph.af1);
        values.put("af2",eph.af2);

        values.put("Crs",eph.Crs);
        values.put("delta_n",eph.delta_n);
        values.put("M0",eph.M0);

        values.put("Cuc",eph.Cuc);
        values.put("es",eph.es);
        values.put("Cus",eph.Cus);
        values.put("sqrtA",eph.sqrtA);

        values.put("toe",eph.toe);
        values.put("Cic",eph.Cic);
        values.put("Omega_0",eph.Omega_0);
        values.put("Cis",eph.Cis);

        values.put("i0",eph.i0);
        values.put("Crc",eph.Crc);
        values.put("w",eph.w);
        values.put("Omega_dot",eph.Omega_dot);

        values.put("i_dot",eph.i_dot);
        values.put("TGD",eph.TGD);

        db.insert("ephBook",null,values);
        values.clear();
    }

    private void initialtDatabase(){
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("Svid",0);

        values.put("af0",0);
        values.put("af1",0);
        values.put("af2",0);

        values.put("Crs",0);
        values.put("delta_n",0);
        values.put("M0",0);

        values.put("Cuc",0);
        values.put("es",0);
        values.put("Cus",0);
        values.put("sqrtA",0);

        values.put("toe",0);
        values.put("Cic",0);
        values.put("Omega_0",0);
        values.put("Cis",0);

        values.put("i0",0);
        values.put("Crc",0);
        values.put("w",0);
        values.put("Omega_dot",0);

        values.put("i_dot",0);
        values.put("TGD",0);
        db.insert("ephBook",null,values);
        values.clear();
    }

    private void updateDatabase(Ephemeris eph){
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        if(eph.af0 != 0){
            values.put("af0",eph.af0);
            values.put("af1",eph.af1);
            values.put("af2",eph.af2);
            values.put("TGD",eph.TGD);
        }
        if(eph.toe != 0){
            values.put("toe",eph.toe);
            values.put("sqrtA",eph.sqrtA);
            values.put("es",eph.es);
            values.put("M0",eph.M0);
            values.put("delta_n",eph.delta_n);
            values.put("Cuc",eph.Cuc);
            values.put("Cus",eph.Cus);
            values.put("Crc",eph.Crc);
        }
        if(eph.Omega_0 != 0){
            values.put("Omega_0",eph.Omega_0);
            values.put("i0",eph.i0);
            values.put("w",eph.w);
            values.put("Omega_dot",eph.Omega_dot);
            values.put("i_dot",eph.i_dot);
            values.put("Crc",eph.Crc);
            values.put("Cic",eph.Cic);
            values.put("Cis",eph.Cis);
        }
        db.update("ephBook",values,"Svid  = ?",new String[]{String.valueOf(eph.Svid)});
    }

}



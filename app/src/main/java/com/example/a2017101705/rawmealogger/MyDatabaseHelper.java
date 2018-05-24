package com.example.a2017101705.rawmealogger;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.widget.Toast;

/**
 * Created by 2017101705 on 2018/5/13.
 */

public class MyDatabaseHelper extends SQLiteOpenHelper {

    public static final String CREATE_EPHBOOK = "create table ephBook("
            +"id integer primary key autoincrement,"
            +"Svid integer,"

            +"af0 real"
            +"af1 real"
            +"af2 real"

            +"Crs real,"
            +"delta_n real,"
            +"M0 real,"

            +"Cuc real,"
            +"es real,"
            +"Cus real,"
            +"sqrtA real,"

            +"toe integer,"
            +"Cic real,"
            +"Omega_0 real"
            +"Cis real,"

            +"i0 real,"
            +"Crc real,"
            +"w real,"
            +"Omega_dot real,"

            +"i_dot real,"
            +"TGD real)";

    private Context mContext;
    public MyDatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version){
        super(context,name,factory,version);
        mContext =context;
    }
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_EPHBOOK);
        Toast.makeText(mContext,"Create succeeded",Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }
}

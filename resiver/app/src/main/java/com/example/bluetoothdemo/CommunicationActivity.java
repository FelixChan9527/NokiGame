package com.example.bluetoothdemo;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bluetoothdemo.utils.ToastUtil;

import org.w3c.dom.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class CommunicationActivity extends AppCompatActivity {
    private String mAddress, mName;
    private BluetoothAdapter mBlueToothAdapter;
    private BluetoothDevice mDevice;
    private BluetoothSocket mBluetoothSocket;
    private final UUID mUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");//蓝牙串口服务的UUID
    private ToastUtil mToast;
    private static OutputStream mOS;
    private String TAG = "CommunicationActivity";
    byte[] buffer = new byte[1];
    private Button btn0;
    private Button btn1;
    private Button btn2;
    private Button btn3;
    private Button btn4;
    private Button btn5;
    private Button btn6;
    private Button btn7;
    private Button btn8;
    private Button btn9;
    private Button btn_star;
    private Button btn_pound;
    private Button btn_opt;
    private Button btn_rt;
    private Button btn_up;
    private Button btn_down;
    private Button btn_left;
    private Button btn_right;
    private Button btn_fire;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);//remove title bar  即隐藏标题栏
        getSupportActionBar().hide();// 隐藏ActionBar
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);//remove notification bar  即全屏
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                                                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_main);

        setContentView(R.layout.communication_layout);
        Intent intent = getIntent();
        mToast = new ToastUtil(this);
        //得到传输过来的设备地址
        mAddress = intent.getStringExtra("address");
        mName = intent.getStringExtra("name");
        //开始连接
        connectDevice();
        button_init();
    }

    public void button_init(){
        btn_listener_init(btn0, R.id.btn0, 0x18, 0x37);
        btn_listener_init(btn1, R.id.btn1, 0x08, 0x27);
        btn_listener_init(btn2, R.id.btn2, 0x09, 0x28);
        btn_listener_init(btn3, R.id.btn3, 0x10, 0x29);
        btn_listener_init(btn4, R.id.btn4, 0x11, 0x30);
        btn_listener_init(btn5, R.id.btn5, 0x12, 0x31);
        btn_listener_init(btn6, R.id.btn6, 0x13, 0x32);
        btn_listener_init(btn7, R.id.btn7, 0x14, 0x33);
        btn_listener_init(btn8, R.id.btn8, 0x15, 0x34);
        btn_listener_init(btn9, R.id.btn9, 0x16, 0x35);
        btn_listener_init(btn_up, R.id.btn_up, 0x01, 0x20);
        btn_listener_init(btn_down, R.id.btn_down, 0x02, 0x21);
        btn_listener_init(btn_left, R.id.btn_left, 0x03, 0x22);
        btn_listener_init(btn_right, R.id.btn_right, 0x04, 0x23);
        btn_listener_init(btn_pound, R.id.btn_pound, 0x19, 0x38);
        btn_listener_init(btn_star, R.id.btn_star, 0x17, 0x36);
        btn_listener_init(btn_fire, R.id.btn_fire, 0x05, 0x24);
        btn_listener_init(btn_opt, R.id.btn_opt, 0x06, 0x25);
        btn_listener_init(btn_rt, R.id.btn_rt, 0x07, 0x26);
    }

    @SuppressLint("ClickableViewAccessibility")
    public void btn_listener_init(Button btn, int resId, int press_num, int release_num){
        btn = (Button) findViewById(resId);
        btn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    buffer[0] = (byte) press_num;
                    sendMessage(buffer);
                }else if(event.getAction() == MotionEvent.ACTION_UP){
                    buffer[0] = (byte) release_num;
                    sendMessage(buffer);
                }
                return false;
            }
        });

    }

    /**
     * 发送数据的方法
//     * @param contentStr
     */
    private void sendMessage(byte[] buffer) {   // 写数据缓存，协议字节多长，就定多长，一个十六进制数是1个字节
        if (mBluetoothSocket.isConnected()) {
            try {
                //获取输出流
                mOS = mBluetoothSocket.getOutputStream();
/*******************在此填写通信协议***************************************/
                if (mOS != null) {
                    mOS.write(buffer);    // 发送A
                    mToast.showToast("发送成功");
/*******************在此填写通信协议***************************************/
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else{
            mToast.showToast("没有设备已连接");
        }
    }

    /**
     * 与目标设备建立连接
     */


    private void connectDevice() {
        //获取默认蓝牙设配器
        mBlueToothAdapter = BluetoothAdapter.getDefaultAdapter();
//        mBlueToothAdapter.cancelDiscovery();
        //通过地址拿到该蓝牙设备device
        mDevice = mBlueToothAdapter.getRemoteDevice(mAddress);
        try {
            //建立socket通信
            mBluetoothSocket = mDevice.createRfcommSocketToServiceRecord(mUUID);
            mBluetoothSocket.connect();
            if (mBluetoothSocket.isConnected()) {
                mToast.showToast("连接成功");
                //开启接收数据的线程
                ReceiveDataThread thread = new ReceiveDataThread();
                thread.start();
            }else{
                mToast.showToast("连接失败，结束重进");
            }
        } catch (IOException e) {
            e.printStackTrace();
            mToast.showToast("连接出错！ ");
            finish();
            try {
                mBluetoothSocket.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (mBluetoothSocket.isConnected()) {
                //关闭socket
                mBluetoothSocket.close();
                mBlueToothAdapter = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 负责接收数据的线程
     */
    public class ReceiveDataThread extends Thread{

        private InputStream inputStream;

        public ReceiveDataThread() {
            super();
            try {
                //获取连接socket的输入流
                inputStream = mBluetoothSocket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void run() {
            super.run();
            int len = 0;
            byte[] buffer = new byte[256];
            while (true){
                try {
                    inputStream.read(buffer);
                    for (byte b : buffer) {
                        Log.d(TAG,"b:" + b);
                    }
                    //设置GBK格式可以获取到中文信息，不会乱码
                    //这里的长度减3是因为使用蓝牙助手调试的原因，正常项目不需要减3
                    String a = new String(buffer,0,buffer.length - 3,"GBK");
                    Log.d(TAG,"a:" + a);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //将收到的数据显示在TextView上
//                            mReceiveContent.append(a);
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

//    @Override
//    public void onWindowFocusChanged(boolean hasFocus){
//        super.onWindowFocusChanged(hasFocus);
//        if (hasFocus && Build.VERSION.SDK_INT >= 26){
//            View decorView = getWindow().getDecorView();
//            decorView.setSystemUiVisibility(
//                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
//                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
//                        | View.SYSTEM_UI_FLAG_FULLSCREEN
//                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
//            );
//        }
//    }
}



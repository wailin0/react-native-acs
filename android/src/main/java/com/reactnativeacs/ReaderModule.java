package com.reactnativeacs;


import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.acs.smartcard.ReaderException;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;


import com.acs.smartcard.Reader;
import com.acs.smartcard.Reader.OnStateChangeListener;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@ReactModule(name = ReaderModule.NAME)
public class ReaderModule extends ReactContextBaseJavaModule {

  public static final String NAME = "ReaderModule";
  private static final String TAG = "ReactNative";
  private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
  public static final String ACTION_USB_PERMISSION = "com.reactnativeacs.USB_PERMISSION";
  private static final String[] stateStrings = { "Unknown", "Absent",
    "Present", "Swallowed", "Powered", "Negotiable", "Specific" };

  private ReactApplicationContext reactContext;
  private Reader mReader;
  private UsbDevice device;
  private Promise InitPromise;

  public  ReaderModule(ReactApplicationContext reactContext){
    super(reactContext);
    this.reactContext = reactContext;
  }

  @NonNull
  @Override
  public String getName() {
    return NAME;
  }

  @ReactMethod
  public void Init(Promise promise){
    UsbManager manager = (UsbManager) this.reactContext.getSystemService(reactContext.USB_SERVICE);
    this.mReader = new Reader(manager);
    this.mReader.setOnStateChangeListener(this.stateListener);
    this.InitPromise = promise;

    try {
      for(UsbDevice device: manager.getDeviceList().values()){
        if (mReader.isSupported(device)){
          this.device = device;
          break;
        }
      }

      if (this.device == null){
        this.rejectConnectionPromise("E100","No Device found");
      }else{
        PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this.reactContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        this.reactContext.registerReceiver(usbReceiver, filter);
        manager.requestPermission(this.device, usbPermissionIntent);
      }
    }catch (NullPointerException np){
      this.rejectConnectionPromise("E100","No Device found");
    }
  }

  @ReactMethod
  public void ConnectToCard(int slotNum,Promise promise){
    int action = Reader.CARD_WARM_RESET;

    try{

      byte[] atr = this.mReader.power(slotNum,action);
      this.mReader.setProtocol(slotNum, Reader.PROTOCOL_T0 | Reader.PROTOCOL_T1);

      promise.resolve(this.bytesToHexString(atr));
    }catch (Exception e){
      promise.reject("R002",e.getMessage());
    }
  }

  @ReactMethod
  public void Transmit(int slotNum,String command,Promise promise){
    try {
      byte[] commandByte = hexStringToBytes(command);
      byte[] responseBuffer = new byte[300];
      int responseLength;

      Log.i(TAG, command);

      responseLength = this.mReader.transmit(slotNum,commandByte,commandByte.length,responseBuffer,responseBuffer.length);
      promise.resolve(bytesToHexString(Arrays.copyOfRange(responseBuffer,0,responseLength)));

    }catch (Exception e){
      promise.reject("R002",e.getMessage());
    }
  }

  @ReactMethod
  public void GetReaderInfo(Promise promise){
    if(this.mReader.isOpened()){
      WritableMap info = Arguments.createMap();
      info.putString("readerName",this.mReader.getReaderName());
      info.putInt("slotNum",this.mReader.getNumSlots());

      promise.resolve(info);
    }else{
      promise.reject("E004","No reader is opened");
    }
  }

  @ReactMethod
  public void CloseReader(){
    if(this.mReader.isOpened()){
      this.mReader.close();
    }
  }

  private OnStateChangeListener stateListener = new OnStateChangeListener() {
    @Override
    public void onStateChange(int slotNum, int prevState, int currState) {
      if (prevState < Reader.CARD_UNKNOWN || prevState > Reader.CARD_SPECIFIC) {
        prevState = Reader.CARD_UNKNOWN;
      }

      if (currState < Reader.CARD_UNKNOWN || currState > Reader.CARD_SPECIFIC) {
        currState = Reader.CARD_UNKNOWN;
      }

      WritableMap eventParams = Arguments.createMap();
      eventParams.putString("currState",stateStrings[currState]);
      eventParams.putString("prevState",stateStrings[prevState]);
      eventParams.putInt("slotNum",slotNum);

      sendEvent("onStateChange",eventParams);
    }
  };

  private void sendEvent(String eventName,@Nullable WritableMap params){
    this.reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(eventName,params);
  }

  private void rejectConnectionPromise(String code,String message){
    this.InitPromise.reject(code,message);
  }

  private void setDevice(UsbDevice device){
    try {
      this.mReader.open(device);

      this.InitPromise.resolve(null);
    }catch (Exception e){
      rejectConnectionPromise("R001",e.getMessage());
    }
  }

  private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (ACTION_USB_PERMISSION.equals(action)) {
        synchronized (this) {
          UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

          if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
            if (device != null) {
              synchronized (this){
                if (mReader.isSupported(device)){
                  setDevice(device);
                }else{
                  rejectConnectionPromise("E100","No Device found");
                }
              }
            }else{
              rejectConnectionPromise("E101", "Device is null");
            }
          }
          else {
            Log.d(TAG, "permission denied for device " + device);
            rejectConnectionPromise("E102", "Permission denied for device");
          }
        }
      }
    }
  };

  private static String bytesToHexString(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for (int i = 0; i < bytes.length; i++) {
      int v = bytes[i] & 0xFF;
      hexChars[i * 2] = HEX_ARRAY[v >>> 4];
      hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
    }
    return new String(hexChars);
  }

  private static byte[] hexStringToBytes(String hex) {
    int len = hex.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      data[i / 2] = (byte)((Character.digit(hex.charAt(i), 16) << 4)
        + Character.digit(hex.charAt(i + 1), 16));
    }
    return data;
  }
}
